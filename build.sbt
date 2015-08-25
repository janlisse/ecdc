import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

name := "ecdc"

lazy val commonSettings = Seq(
  resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  resolvers += "oncue-bintray" at "http://dl.bintray.com/oncue/releases",
  organization := "com.github.janlisse",
  version := "0.1.0-SNAPSHOT",
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

lazy val api = project.in(file("src/api"))
  .dependsOn(core, `aws-s3`, `aws-ecs`, crypto, git)
  .settings(commonSettings: _*)
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .settings(
    routesImport += "ecdc.api.bindables._",
    routesGenerator := InjectedRoutesGenerator,
    dockerExposedPorts := Seq(9000),
    dockerEntrypoint := Seq("bin/ecdc"),
    dockerBaseImage := "janlisse/java-8-server",
    dockerUpdateLatest := true,
    packageName in Docker := "ecdc",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.10",
      "org.scaldi" %% "scaldi-play" % "0.5.8",
      "org.scaldi" %% "scaldi-akka" % "0.5.6",
      "org.scalatest" %% "scalatest" % "2.2.5" % "test"
    )
  )

lazy val core = project.in(file("src/core"))
  .dependsOn(crypto)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
   //   "oncue.knobs" %% "core" % "3.3.0",
   //   "org.scalaz" %% "scalaz-core" % "7.1.3",
      "com.typesafe.play" % "play-json_2.11" % "2.4.2",
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
      "com.amazonaws" % "aws-java-sdk-s3" % "1.10.10",
      "commons-io" % "commons-io" % "2.4"
    )
  )

lazy val `aws-ecs` = project.in(file("src/aws-ecs"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-ecs" % "1.10.10",
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % "1.10.10",
      "com.typesafe.play" %% "play-json" % "2.4.2"
    )
  )

lazy val crypto = project.in(file("src/crypto"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcprov-jdk15on" % "1.52",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.52"
    )
  )

lazy val git = project.in(file("src/git"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r",
      "com.typesafe.akka" %% "akka-actor" % "2.3.12"
    )
  )
