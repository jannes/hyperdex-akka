package hyperdex

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object ClusterTest {

  sealed trait Role
  case object GatewayHiSayer extends Role
  case object HiSayer extends Role
  case object HiReceiver extends Role

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      val cluster = Cluster(context.system)
      // determine own role
      if (cluster.selfMember.hasRole(HiReceiver.toString)) {
        println("I am receiver")
        context.spawn(DataNode(), "receiverNode")
      } else if (cluster.selfMember.hasRole(HiSayer.toString)) {
        println("I am sender")
        context.spawn(SenderNode(), "senderNode")
      } else if (cluster.selfMember.hasRole(GatewayHiSayer.toString)) {
        println("I am gateway sender")
        context.spawn(GatewayNode("localhost", 8080), "gatewayNode")
      } else {
        println("could not determine my role")
      }
      Behaviors.empty
    }
  }

  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Usage: role port")
    args(0) match {
      case "hisayer" =>
        startup(HiSayer, args(1).toInt)
      case "hireceiver" =>
        startup(HiReceiver, args(1).toInt)
      case "gateway" =>
        startup(GatewayHiSayer, args(1).toInt)
      case _ =>
        println("supplied wrong role")
        System.exit(1)
    }
  }

  def startup(role: Role, port: Int): Unit = {
    // Override the configuration of the port
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [$role]
      """).withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }

}
