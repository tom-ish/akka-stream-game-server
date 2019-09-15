package tomish

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest._

class ServerTestSpec extends FlatSpec with Matchers {
  "object" should "create empty GameService" in {
    new GameService()
  }

  "object" should "connect to the GameService websocket" in {

  }
}

class GameService() {
  def greeter: Flow[Message, Message, Any] = Flow[Message].mapConcat {
    case textMessage : TextMessage =>
      TextMessage(Source.single("Hello") ++ textMessage.textStream ++ Source.single("!")) :: Nil
    case binaryMessage : BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      binaryMessage.dataStream.runWith(Sink.ignore)
      Nil
  }
}
