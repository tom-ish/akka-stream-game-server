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

  test("should connect to the GameService websocket") {
    assertWebSocket("John") { wsClient =>
      // check response for WS upgrade headers
      isWebSocketUpgrade shouldEqual true
    }
  }

  test("should respond with correct message") {
    assertWebSocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\": \"John\"}]")
      wsClient.sendMessage("hello")
      wsClient.expectMessage("hello")
    }
  }

  test("should register player") {
    assertWebSocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\": \"John\"}]")
    }
  }

  test("should register multiple players") {
    val gameService = new GameService()
    val wsClient1 = WSProbe()
    val wsClient2 = WSProbe()

    WS(s"/?playerName=john", wsClient1.flow) ~> gameService.webSocketRoute -> check {
      wsClient1.expectMessage("[{\"name\": \"john\"}]")
    }

    WS(s"/?playerName=john", wsClient1.flow) ~> gameService.webSocketRoute -> check {
      wsClient2.expectMessage("[{\"name\": \"john\"},{\"name\": \"alice\"}]")
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

  def flow(playerName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(playerActorSource) {
    implicit builder => playerActor =>
    import GraphDSL.Implicits._

    val materialization = builder.materializedValue.map(playerActorRef => PlayerJoined(Player(playerName), playerActorRef))
     val merge = builder.add(Merge[GameEvent](2))

    val messagesToGameEventsFlow = builder.add(Flow[Message].map {
      case TextMessage.Strict(txt) => PlayerMoveRequest(playerName, txt)
    })

    val gameEventsToMessagesFlow = builder.add(Flow[GameEvent].map {
      case PlayerChanged(players) => {
        import spray.json._
        import DefaultJsonProtocol._

        implicit val playerFormat = jsonFormat1(Player)
        TextMessage(players.toJson.toString)
      }
      case PlayerMoveRequest(playerName, direction) =>  TextMessage(direction)
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
    case msg: PlayerMoveRequest => notifyPlayerMoveRequested(msg)
  }

  def notifyPlayerMoveRequested(playerMoveRequest: PlayerMoveRequest): Unit = {
    players.values.foreach(_.actor ! playerMoveRequest)
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

case class Player(name: String)
case class PlayerWithActor(player: Player, actor: ActorRef)

