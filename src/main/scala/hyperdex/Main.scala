package hyperdex

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

import com.typesafe.config.ConfigFactory

object Main {

  sealed trait Role
  case object GatewayNodeRole extends Role
  case object DataNodeRole extends Role

  val RNG = new scala.util.Random // For randomly generating port number offsets

  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Usage: role port")
    val port = args(1).toInt + RNG.nextInt(12345) // Random port

    args(0) match {
      case "data" =>
        startup(DataNodeRole, port)
      case "gateway" =>
        startup(GatewayNodeRole, port)
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



    role match {
      case DataNodeRole => {
        val system =
          ActorSystem[Nothing](DataNodeRootBehavior(), "ClusterSystem", config)

      }
      case GatewayNodeRole => {
        val system =
          ActorSystem(GatewayNode.actorBehavior(), "ClusterSystem", config)
        GatewayHttpServer.run("0.0.0.0", 8080, system)


      }
    }
  }

  object DataNodeRootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      ctx.log.info("I am receiver")
      ctx.spawn(DataNode(), "receiverNode")
      Behaviors.empty
    }
  }
}
