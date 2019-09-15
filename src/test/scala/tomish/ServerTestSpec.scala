package tomish

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import org.scalatest._

class ServerTestSpec extends FunSuite with Matchers with ScalatestRouteTest  {
  test("should create empty GameService") {
    new GameService()
  }

  test("should be able to connect to the GameService websocket") {
    assertWebSocket("John") { wsClient =>
      // check response for WS upgrade headers
      isWebSocketUpgrade shouldEqual true
    }
  }

  test("should register player") {
    assertWebSocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 0}}]")
    }
  }

  test("should register multiple players") {
    val gameService = new GameService()
    val wsClient1 = WSProbe()
    val wsClient2 = WSProbe()

    WS(s"/?playerName=John", wsClient1.flow) ~> gameService.webSocketRoute -> check {
      wsClient1.expectMessage("[{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 0}}]")
    }

    WS(s"/?playerName=Aohn", wsClient1.flow) ~> gameService.webSocketRoute -> check {
      wsClient2.expectMessage("[{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 0}}," +
        "{\"name\": \"Alice\", \"position\": {\"x\": 0, \"y\": 0}}]")
    }
  }

  test("should register player and move it up") {
    assertWebSocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 0}}]")
      wsClient.sendMessage("up")
      wsClient.expectMessage("{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 1}}")
    }
  }

  test("should register player and move around") {
    assertWebSocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 0}}]")
      wsClient.sendMessage("up")
      wsClient.expectMessage("{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 1}}")
      wsClient.sendMessage("left")
      wsClient.expectMessage("{\"name\": \"John\", \"position\": {\"x\": -1, \"y\": 1}}")
      wsClient.sendMessage("down")
      wsClient.expectMessage("{\"name\": \"John\", \"position\": {\"x\": -1, \"y\": 0}}")
      wsClient.sendMessage("right")
      wsClient.expectMessage("{\"name\": \"John\", \"position\": {\"x\": 0, \"y\": 0}}")
    }
  }

  def assertWebSocket(playerName: String)(assertions: WSProbe => Unit) : Unit = {
    val gameService = new GameService()
    val wsClient = WSProbe()

    WS(s"/?playerName=$playerName", wsClient.flow) ~> gameService.webSocketRoute -> check {
      assertions(wsClient)
    }
  }
}

class GameService() extends Directives {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  val webSocketRoute = (get & parameter("playerName")) { playerName =>
    handleWebSocketMessages(flow(playerName))
  }

  val gameAreaActor = actorSystem.actorOf(Props[GameAreaActor], "gameAreaActor")
  val playerActorSource = Source.actorRef[GameEvent](5, OverflowStrategy.fail)

  def flow(playerName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(playerActorSource) { implicit builder => playerActor =>
    import GraphDSL.Implicits._

    val materialization = builder.materializedValue.map(playerActorRef =>
        PlayerJoined(Player(playerName, Position(0, 0)), playerActorRef))

    val merge = builder.add(Merge[GameEvent](2))

    val messagesToGameEventsFlow = builder.add(Flow[Message].map {
      case TextMessage.Strict(direction) => PlayerMoveRequest(playerName, direction)
    })

    val gameEventsToMessagesFlow = builder.add(Flow[GameEvent].map {
      case PlayerChanged(players) => {
        import spray.json._
        import DefaultJsonProtocol._

        implicit val positionFormat = jsonFormat2(Position)
        implicit val playerFormat = jsonFormat2(Player)
        TextMessage(players.toJson.toString)
      }
    })

    val gameAreaActorSink = Sink.actorRef[GameEvent](gameAreaActor, PlayerLeft(playerName))

    materialization ~> merge ~> gameAreaActorSink
    messagesToGameEventsFlow ~> merge


    playerActor ~> gameEventsToMessagesFlow

    FlowShape(messagesToGameEventsFlow.in, gameEventsToMessagesFlow.out)
  })
}

class GameAreaActor extends Actor {
  val players = collection.mutable.LinkedHashMap[String, PlayerWithActor]()

  override def receive: Receive = {
    case PlayerJoined(player, actor) => {
      players += (player.name -> PlayerWithActor(player, actor))
      notifyPlayersChanged()
    }
    case PlayerLeft(playerName) => {
      players -= (playerName)
      notifyPlayersChanged()
    }
    case PlayerMoveRequest(playerName, direction) => {
      val offset = direction match {
        case "UP" => Position(0, 1)
        case "DOWN" => Position(0, -1)
        case "RIGHT" => Position(1, 0)
        case "LEFT"=> Position(-1, 0)
      }
      val oldPlayerWithActor = players(playerName)
      val oldPlayer = oldPlayerWithActor.player
      val actor = oldPlayerWithActor.actor

      players(playerName) = PlayerWithActor(Player(playerName, oldPlayer.position + offset), actor)

      notifyPlayersChanged()
    }
  }

  def notifyPlayersChanged(): Unit = {
    players.values.foreach(_.actor ! PlayerChanged(players.values.map(_.player)))
  }
}

trait GameEvent
case class PlayerJoined(player: Player, actor: ActorRef) extends GameEvent
case class PlayerLeft(playerName: String) extends GameEvent
case class PlayerMoveRequest(playerName: String, direction: String) extends GameEvent
case class PlayerChanged(players: Iterable[Player]) extends GameEvent

case class Player(name: String, position: Position)
case class PlayerWithActor(player: Player, actor: ActorRef)

case class Position(x: Int, y: Int) {
  def +(position: Position) = {
    Position(x + position.x, y + position.y)
  }
}

