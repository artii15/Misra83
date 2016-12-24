package misra.demo.actors

import akka.actor.{Actor, Props}
import misra.actors.TokensBroker
import misra.demo.messages.{LoseToken, Start}
import misra.messages.tokens.misra.{Ping, Pong}
import misra.messages.{NeighbourAnnouncement, NeighbourRegistrationAck}

class RingSupervisor(ringSize: Int) extends Actor {
  private var unconfirmedAck = ringSize
  private var pairs = List[ConsumerBrokerPair]()

  override def receive: Receive = {
    case Start => initializeRing()
    case NeighbourRegistrationAck => receiveNeighbourRegistrationAck()
    case LoseToken => pairs.headOption.foreach(_.consumer ! LoseToken)
  }

  private def initializeRing(): Unit = {
    pairs = Range.inclusive(1, ringSize).foldLeft(List[ConsumerBrokerPair]())((pairs, nodeId) => {
      val consumer = context.actorOf(Props(classOf[TokensConsumer], nodeId, ringSize))
      val broker = context.actorOf(Props(classOf[TokensBroker], consumer, ringSize))
      ConsumerBrokerPair(consumer, broker) :: pairs
    })
    val lastPair = pairs.tail.foldLeft(pairs.head)((prevPair, currentPair) => {
      prevPair.broker ! NeighbourAnnouncement(currentPair.broker)
      currentPair
    })
    lastPair.broker ! NeighbourAnnouncement(pairs.head.broker)
  }

  private def receiveNeighbourRegistrationAck(): Unit = {
    unconfirmedAck -= 1
    if(unconfirmedAck == 0) {
      val firstPair = pairs.headOption
      firstPair.foreach(_.broker ! Ping(0))
      firstPair.foreach(_.broker ! Pong(0))
    }
  }
}