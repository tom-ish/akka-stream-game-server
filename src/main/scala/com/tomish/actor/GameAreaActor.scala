package com.tomish.actor

import akka.actor.{Actor, ActorRef}

trait GameEvent
case class PlayerJoined(player: Player, actor: ActorRef) extends GameEvent
case class PlayerLeft(playerName: String) extends GameEvent
case class PlayerMoveRequest(playerName: String, direction: String) extends GameEvent
case class PlayerChanged(players: Iterable[Player]) extends GameEvent

case class Player(name: String, position: Position)
case class PlayerWithActor(player: Player, actor: ActorRef)

case class Position(x: Int, y: Int) {
  def +(position: Position) = Position(x + position.x, y + position.y)
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
        case "UP" => Position(0, -1)
        case "DOWN" => Position(0, 1)
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
