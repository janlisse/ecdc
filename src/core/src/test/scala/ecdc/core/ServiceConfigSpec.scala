package ecdc.core

import java.io.File

import com.typesafe.config.ConfigFactory
import ecdc.core.TaskDef.{ Environment, LogConfiguration }
import model.{ Cluster, Service, Version }
import testutils.Spec

class ServiceConfigSpec extends Spec {

  val baseDir = new File("./src/core/src/test/resources/testRepo")
  val service = Service("foo")
  val cluster = Cluster("production")
  val version = Version.latest

  it should "apply traits to service.conf" in {
    val baseConf = ConfigFactory.parseFile(baseDir.toPath.resolve(s"service/${service.name}/service.conf").toFile)
    val config = ServiceConfig.applyTraits(Seq(DefaultServiceTrait("loadbalancer")), baseConf, baseDir, cluster)
    config.hasPath("loadbalancer") shouldBe true
  }

  it should "replace taskdef placeholders with variables" in {
    val sc = ServiceConfig.read(service, cluster, version, baseDir, Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    val td = sc.taskDefinition
    td.containerDefinitions.head.memory shouldBe 1024
    td.containerDefinitions.head.command shouldBe Seq("-Dlogger.resource=production/logback.xml")
  }

  it should "add all variables as environment vars" in {
    val sc = ServiceConfig.read(service, cluster, version, baseDir, Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    val td = sc.taskDefinition
    td.containerDefinitions.head.environment shouldBe Seq(
      Environment("MEMORY", "1024"),
      Environment("CLUSTER", "production"))
  }

  it should "tolerate missing service.conf" in {
    val sc = ServiceConfig.read(Service("baz"), cluster, version, baseDir, Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Seq(DefaultServiceTrait("webapp")))
    val td = sc.taskDefinition
    td.containerDefinitions.head.environment shouldBe Seq(
      Environment("MEMORY", "1024"),
      Environment("CLUSTER", "production"))
  }

  it should "read loadBalancer config" in {
    val sc = ServiceConfig.read(Service("bar"), cluster, version, baseDir, Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    val lb = sc.loadBalancer
    lb shouldBe Some(LoadBalancer(
      instancePort = 9007,
      loadBalancerPort = 80,
      protocol = "http",
      instanceProtocol = "http",
      scheme = "internal",
      serviceRole = "ecsService",
      subnets = Seq("subnet-fc0bed99", "subnet-46898a32", "subnet-7aab883c"),
      securityGroups = Seq("sg-2938234c"),
      healthCheck = HealthCheck(
        target = "HTTP:9007/status",
        healthyThreshold = 10,
        unhealthyThreshold = 2,
        interval = 30,
        timeout = 5
      )
    ))
  }

  it should "use the specified version" in {
    val specificVersion = Version("45.crazysha1")
    val sc = ServiceConfig.read(Service("bar"), cluster, specificVersion, baseDir, Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    val td = sc.taskDefinition

    td.containerDefinitions.head.image.tag shouldBe "45.crazysha1"
  }

  it should "extract log configuration" in {
    val sc = ServiceConfig.read(Service("bar"), cluster, Version.latest, baseDir, Map("MEMORY" -> "1024", "CLUSTER" -> "production"), Nil)
    val cd = sc.taskDefinition.containerDefinitions.head

    cd.logConfiguration shouldBe Some(LogConfiguration(
      logDriver = "awslogs",
      options = Map("awslogs-group" -> "awslogs-test", "awslogs-region" -> "eu-west-1")
    ))
  }
}
