package hyperdex

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory
import hyperdex.GatewayNode.hyperSpaces

object Main {

  sealed trait Role
  case object GatewayNodeRole extends Role
  case object DataNodeRole extends Role

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
    var hyperSpace = HyperSpaceCreator.initTestHyperSpace();
    var hyperSpaceNodes: Array[HyperSpaceNode] = HyperSpaceCreator.initHyperspace(hyperSpace, 1);
    hyperSpaces = Map("test" -> hyperSpace)
    role match {
      case DataNodeRole => {
        val system =
          ActorSystem[Nothing](DataNodeRootBehavior(hyperSpaceNodes(0)), "ClusterSystem", config)
      }
      case GatewayNodeRole => {
        val system =
          ActorSystem(GatewayNode.actorBehavior(hyperSpaces), "ClusterSystem", config)
        GatewayHttpServer.run("localhost", 8080, system)
      }
    }
  }

  object DataNodeRootBehavior {
    def apply(hyperSpaceNode: HyperSpaceNode): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      println("I am receiver")
      context.spawn(DataNode(hyperSpaceNode), "receiverNode")
      Behaviors.empty
    }
  }
}
