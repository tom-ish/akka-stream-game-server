package com.tomish

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import com.tomish.service.GameService
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