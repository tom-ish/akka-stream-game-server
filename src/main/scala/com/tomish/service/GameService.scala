package com.tomish.service

import akka.Done
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import com.tomish.actor._
import spray.json.JsonParser

import scala.collection.immutable.{AbstractMap, SeqMap, SortedMap}
import scala.concurrent.duration._

class GameService(implicit val actorSystem: ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends Directives {

  val webSocketRoute = (get & parameter("playerName")) { playerName =>
    handleWebSocketMessages(flow(playerName))
  }

  val gameAreaActor = actorSystem.actorOf(Props[GameAreaActor])
  val playerActorSource = Source.actorRef[GameEvent](5, OverflowStrategy.fail)

  def flow(playerName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(playerActorSource) { implicit builder => playerActor =>
    import GraphDSL.Implicits._

    import spray.json._
    import DefaultJsonProtocol._

    implicit val msgFormat = jsonFormat2(Msg[String])

    val materialization = builder.materializedValue.map(playerActorRef => PlayerJoined(playerName, playerActorRef))

    val merge = builder.add(Merge[GameEvent](2))

    val messagesToGameEventsFlow = builder.add(Flow[Message].map {
      case TextMessage.Strict(msg) =>

        val msgJson = msg.parseJson
        msgJson match {
          case JsObject(fields) =>
            fields.get("msgType").get match {
              case JsString(value) =>
                val obj = fields.get("obj").get
                value match {
                  case "PlayerReady" =>
                    PlayerReady(playerName)
                  case "PlayerMoveRequest" =>
                    val direction = obj.asJsObject.fields.get("direction").get.toString.replace(""""""", "")
                    PlayerMoveRequest(playerName, direction)
                }
              case _ => EmptyEvent
            }
          case _ => EmptyEvent
        }
    })

    val gameEventsToMessagesFlow = builder.add(Flow[GameEvent].map {
      case PlayerChanged(players) =>
        implicit val positionFormat = jsonFormat2(Position)
        implicit val playerFormat = jsonFormat6(Player)
        TextMessage(Msg("PlayerChanged", players.toJson.toString).toJson.toString)
      case GameStart =>
        implicit val msgFormat = jsonFormat2(Msg[String])
        TextMessage(Msg("GameStart", "game starting").toJson.toString)
    })

    val gameAreaActorSink = Sink.actorRef[GameEvent](gameAreaActor, PlayerLeft(playerName))

    materialization          ~> merge ~> gameAreaActorSink
    messagesToGameEventsFlow ~> merge


    playerActor ~> gameEventsToMessagesFlow

    FlowShape(messagesToGameEventsFlow.in, gameEventsToMessagesFlow.out)
  })
}
