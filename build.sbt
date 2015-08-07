name := "ecdc"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.7"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)

libraryDependencies ++= Seq(
  "org.bouncycastle" % "bcprov-jdk15on" % "1.52",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.52",
  "commons-codec" % "commons-codec" % "1.10",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.10.10",
  "com.amazonaws" % "aws-java-sdk-ecs" % "1.10.10",
  "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % "1.10.10",
  "commons-io" % "commons-io" % "2.4",
  "org.scaldi" %% "scaldi-play" % "0.5.8",
  "org.scaldi" %% "scaldi-akka" % "0.5.6",
  "org.scalatest" %% "scalatest" % "2.2.5" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

dockerExposedPorts := Seq(9000)
dockerEntrypoint := Seq("bin/ecdc")
dockerBaseImage := "janlisse/java-8-server"
dockerUpdateLatest := true
packageName in Docker := "ecdc"
