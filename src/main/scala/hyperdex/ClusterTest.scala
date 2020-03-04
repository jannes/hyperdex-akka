package hyperdex

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object ClusterTest {

  sealed trait Role
  case object GatewayNodeRole extends Role
  case object DataNodeRole extends Role

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      val cluster = Cluster(context.system)
      // determine own role
      if (cluster.selfMember.hasRole(DataNodeRole.toString)) {
        println("I am receiver")
        context.spawn(DataNode(), "receiverNode")
      } else if (cluster.selfMember.hasRole(GatewayNodeRole.toString)) {
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
      case "data" =>
        startup(DataNodeRole, args(1).toInt)
      case "gateway" =>
        startup(GatewayNodeRole, args(1).toInt)
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
