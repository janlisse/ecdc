import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

name := "ecdc"

lazy val commonSettings = Seq(
  resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  resolvers += "oncue-bintray" at "http://dl.bintray.com/oncue/releases",
  organization := "com.trademachines",
  version := sys.props.getOrElse("version", default = "0"),
  scalaVersion := "2.11.7",
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(RewriteArrowSymbols, true)
) ++ scalariformSettings

lazy val root = project.in(file("."))
  .aggregate(api, core, `aws-s3`, `aws-ecs`, crypto, git)
  .settings(commonSettings: _*)

lazy val cli = project.in(file("src/cli"))
  .dependsOn(core, crypto)
  .settings(commonSettings: _*)

lazy val api = project.in(file("src/api"))
  .dependsOn(core, `aws-s3`, `aws-ecs`, crypto, git)
  .settings(commonSettings: _*)
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .settings(
    routesImport += "ecdc.api.bindables._",
    routesGenerator := InjectedRoutesGenerator,
    dockerExposedPorts := Seq(9000),
    dockerBaseImage := "janlisse/java-8-server",
    dockerUpdateLatest := true,
    dockerRepository := Some("608300940987.dkr.ecr.eu-west-1.amazonaws.com"),
    packageName in Docker := "ecdc",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.10",
      "org.scaldi" %% "scaldi-play" % "0.5.10",
      "org.scalatest" %% "scalatest" % "2.2.5" % "test"
    )
  )

lazy val core = project.in(file("src/core"))
  .dependsOn(crypto)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" % "play-json_2.11" % "2.4.3",
      "com.typesafe" % "config" % "1.3.0",
      "org.json4s" %% "json4s-native" % "3.3.0",
      "org.slf4j" % "slf4j-api" % "1.7.12",
      "org.scalatest" %% "scalatest" % "2.2.5" % "test",
      "org.mockito" % "mockito-all" % "1.10.19" % "test"
    )
  )

lazy val `aws-s3` = project.in(file("src/aws-s3"))
  .dependsOn(crypto)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % "1.10.32",
      "commons-io" % "commons-io" % "2.4"
    )
  )

lazy val `aws-ecs` = project.in(file("src/aws-ecs"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-ecs" % "1.10.32",
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % "1.10.32",
      "com.typesafe.play" %% "play-json" % "2.4.3"
    )
  )

lazy val crypto = project.in(file("src/crypto"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcprov-jdk15on" % "1.53",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.53"
    )
  )

lazy val git = project.in(file("src/git"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "4.1.0.201509280440-r",
      "com.typesafe.akka" %% "akka-actor" % "2.3.13"
    )
  )
