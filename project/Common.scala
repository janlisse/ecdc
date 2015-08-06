import com.typesafe.sbt.SbtNativePackager.autoImport._
import sbt.Keys._
import sbt._

object Common {

  private val buildInfoGenerator = (sourceManaged in Compile, version, name) map { (d, v, n) =>
    val file = d / "tm/Info.scala"
    IO.write(file, """package tm
                     |object Info {
                     |  val version = "%s"
                     |  val name = "%s"
                     |}
                     | """.stripMargin.format(v, n))
    Seq(file)
  }

  val settings: Seq[Setting[_]] = Seq(
    organization := "com.trademachines",
    maintainer := "trademachines",
    version := sys.props.getOrElse("version", default = "0"),
    scalaVersion := "2.11.6",
    scalacOptions ++= Seq("-feature", "-deprecation"),
    sourceGenerators in Compile <+= buildInfoGenerator
  )
}
