package ecdc.core

import java.io.File

import com.typesafe.config.ConfigFactory
import ecdc.core.TaskDef.Environment
import model.{ Service, Cluster }
import testutils.Spec

class ServiceConfigSpec extends Spec {

  val baseDir = new File("./src/core/src/test/resources/testRepo")
  val service = Service("foo")
  val sc = new ServiceConfig(service, Cluster("production"), baseDir)

  it should "apply traits to service.conf" in {
    val baseConf = ConfigFactory.parseFile(baseDir.toPath.resolve(s"service/${service.name}/service.conf").toFile)
    val config = sc.applyTraits(Seq(ServiceTrait("loadbalancer")), baseConf)
    config.hasPath("loadbalancer") shouldBe true
  }

  it should "replace taskdef placeholders with variables" in {
    val td = sc.readTaskDefinition(Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    td.containerDefinitions.head.memory shouldBe 1024
    td.containerDefinitions.head.command shouldBe Seq("-Dlogger.resource=production/logback.xml")
  }

  it should "add all variables as environment vars" in {
    val td = sc.readTaskDefinition(Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    td.containerDefinitions.head.environment shouldBe Seq(
      Environment("MEMORY", "1024"),
      Environment("CLUSTER", "production"))
  }
}
