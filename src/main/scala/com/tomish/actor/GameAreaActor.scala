package com.tomish.actor

import java.awt.Color

import akka.actor.{Actor, ActorRef}
import com.tomish.GameArea
import com.tomish.utils.Tools

trait GameEvent
case class PlayerJoined(playerName: String, actor: ActorRef) extends GameEvent
case class PlayerLeft(playerName: String) extends GameEvent
case class PlayerMoveRequest(playerName: String, direction: String) extends GameEvent
case class PlayerChanged(players: Iterable[Player]) extends GameEvent
case class PlayerReady(playerName: String) extends GameEvent
case object PlayerLost extends GameEvent
case object GameStart extends GameEvent
case object EmptyEvent extends GameEvent

case class Player(name: String, color: String, position: Position, var isReady: Boolean, var canStart: Boolean, var hasLost: Boolean)
case class PlayerWithActor(player: Player, actor: ActorRef)

case class Msg[A](msgType: String, obj: A)

case class Position(x: Int, y: Int) {
  def +(position: Position) = Position(
    (x + position.x + GameArea.width) % GameArea.width,
    (y + position.y + GameArea.height) % GameArea.height)
}

class GameAreaActor extends Actor {
  val players = collection.mutable.LinkedHashMap[String, PlayerWithActor]()
  def occupiedPosition = players.values.map(_.player.position).toList
  def occupiedColor = players.values.map(_.player.color).toList
  val food = initFood()
  var gameRunning = false;

  override def receive: Receive = {
    case PlayerJoined(playerName, actor) =>
      // player initialization
      val newPlayerWithActor = initializePlayer(playerName, actor)
      players += (playerName -> newPlayerWithActor)
      notifyPlayersChanged()
    case PlayerReady(playerName) =>
      var canStart = true
      players.foreach { player =>
        if (player._1 equals playerName)
          player._2.player.isReady = true
        else if(!player._2.player.isReady) canStart = false
      }
      if(canStart) {
        players.foreach { player =>
          player._2.player.canStart = canStart
        }
      }
      notifyPlayersChanged()
    case PlayerLeft(playerName) =>
      players -= (playerName)
      notifyPlayersChanged()
    case GameStart =>
      gameRunning = true
      notifyPlayersGameStart()
    case PlayerMoveRequest(playerName, direction) =>
      val offset = direction match {
        case "UP" => Position(0, -1)
        case "DOWN" => Position(0, 1)
        case "RIGHT" => Position(1, 0)
        case "LEFT" => Position(-1, 0)
      }
      val oldPlayerWithActor = players(playerName)
      val oldPlayer = oldPlayerWithActor.player
      val actor = oldPlayerWithActor.actor
      val newPosition = oldPlayer.position + offset

      if(!occupiedPosition.contains(newPosition)) {
        val p = players(playerName).player
        players(playerName) = PlayerWithActor(
          Player(playerName, p.color, newPosition, p.isReady, p.canStart, false),
          actor
        )
      }

      notifyPlayersChanged()
  }

  def notifyPlayersChanged(): Unit = {
    players.values.foreach { playerWithActor =>
        playerWithActor.actor ! PlayerChanged(players.values.map(_.player))
    }
  }

  def notifyPlayersGameStart() = {
    players.values.foreach { playerWithActor =>
      playerWithActor.actor ! GameStart
    }
  }


  def initializePlayer(playerName: String, actor: ActorRef) = {
    def initColor: String = {
      val newColor = Tools.getRandomColor
      if (occupiedColor.contains(newColor))
        initColor
      else
        newColor
    }

    def initPosition: Position = {
      val newPosition = Tools.getRandomPosition
      if(occupiedPosition.contains(newPosition))
        initPosition
      else
        newPosition
    }

    val newColor = initColor
    val newPosition = initPosition
    PlayerWithActor(Player(playerName, newColor, newPosition, false, false, false), actor)
  }

  def initFood() = {
    def initPosition: Position = {
      val newPosition = Tools.getRandomPosition
      if(occupiedPosition.contains(newPosition))
        initPosition
      else
        newPosition
    }
    initPosition
  }

}
