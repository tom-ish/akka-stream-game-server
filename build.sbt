import Dependencies._

ThisBuild / scalaVersion     := "2.13.0"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.tomish"
ThisBuild / organizationName := "tomish"

val akkaVersion = "2.5.23"
val akkaHttpVersion = "10.1.9"

lazy val root = (project in file("."))
  .settings(
    name := "akka-stream-game-server",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
    // https://mvnrepository.com/artifact/com.typesafe.akka/akka-http
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    // https://mvnrepository.com/artifact/com.typesafe.akka/akka-http-testkit
    libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
    // https://mvnrepository.com/artifact/com.typesafe.akka/akka-http-spray-json
    libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion

  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
