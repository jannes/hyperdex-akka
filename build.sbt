val akkaVersion = "2.6.3"
val akkaHttpVersion = "10.1.11"

lazy val root = (project in file("."))
  .settings(
    name := "hyperdex-akka",
    version := "0.1",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )
