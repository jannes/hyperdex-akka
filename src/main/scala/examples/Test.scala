package examples

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

object Test extends App {

  case class Hi(senderName: String, sender: ActorRef[HiBack])

  sealed trait HiSenderAcceptedMessages
  case class HiBack(senderName: String, sender: ActorRef[Hi]) extends HiSenderAcceptedMessages
  case class SayHi() extends HiSenderAcceptedMessages

  def hiReceiver(): Behavior[Hi] = Behaviors.receive { (context, message) =>
    message.sender ! HiBack("Hi receiver", context.self)
    Behaviors.same
  }

  def hiSender(): Behavior[HiSenderAcceptedMessages] = Behaviors.receive { (context, message) =>
    message match {
      case HiBack(senderName, sender) =>
        context.log.info(s"Hi was received by $senderName")
      case SayHi() =>
        val to = context.spawn(hiReceiver(), "hiReceiver")
        context.log.info("saying hi now")
        to ! Hi("Hi sayer", context.self)
    }
    Behaviors.same
  }

  val system = ActorSystem(hiSender(), "root")

  system ! SayHi()

}
