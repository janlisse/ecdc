package ecdc.core

import java.io.File

import com.typesafe.config.ConfigFactory
import ecdc.core.TaskDef.ContainerDefinition.Image
import ecdc.core.TaskDef._
import model.{ Cluster, Service, Version }
import testutils.Spec
import testutils.ServiceConfigUtils._

class ServiceConfigSpec extends Spec {

  val baseDir = new File("./src/core/src/test/resources/testRepo")
  val service = Service("foo")
  val cluster = Cluster("production")
  val version = Version.latest
  private val defaultVars = Map("MEMORY" -> "1024", "CLUSTER" -> "production")

  it should "apply traits to service.conf" in {
    val baseConf = ConfigFactory.parseFile(baseDir.toPath.resolve(s"service/${service.name}/service.conf").toFile)
    val config = ServiceConfig.applyTraits(Seq(DefaultServiceTrait("loadbalancer")), baseConf, baseDir, cluster)
    config.hasPath("loadbalancer") shouldBe true
  }

  it should "replace taskdef placeholders with variables" in {
    val sc = ServiceConfig.read(service, cluster, version, baseDir, defaultVars)
    val td = sc.taskDefinition
    td.containerDefinitions.head.memory shouldBe Some(1024)
    td.containerDefinitions.head.command shouldBe Seq("-Dlogger.resource=production/logback.xml")
  }

  it should "add all variables as environment vars" in {
    val sc = ServiceConfig.read(service, cluster, version, baseDir, defaultVars)
    val td = sc.taskDefinition
    td.containerDefinitions.head.environment shouldBe Seq(
      Environment("MEMORY", "1024"),
      Environment("CLUSTER", "production"))
  }

  it should "merge variables from container definition" in {
    val sc = ServiceConfig.read(Service("with-environment-vars"), cluster, version, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.containerDefinitions.head.environment shouldBe Seq(
      Environment("MEMORY", "1024"),
      Environment("CLUSTER", "production"),
      Environment("ONE", "two"),
      Environment("FOO", "bar")
    )
  }

  it should "tolerate missing service.conf" in {
    val sc = ServiceConfig.read(Service("baz"), cluster, version, baseDir, defaultVars, Seq(DefaultServiceTrait("webapp")))
    val td = sc.taskDefinition
    td.containerDefinitions.head.environment shouldBe Seq(
      Environment("MEMORY", "1024"),
      Environment("CLUSTER", "production"))
  }

  it should "read loadBalancer config" in {
    val sc = ServiceConfig.read(Service("bar"), cluster, version, baseDir, defaultVars)
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
    val sc = ServiceConfig.read(Service("bar"), cluster, specificVersion, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.containerDefinitions.head.image.tag shouldBe "45.crazysha1"
  }

  it should "extract log configuration" in {
    val sc = ServiceConfig.read(Service("bar"), cluster, Version.latest, baseDir, defaultVars)
    val cd = sc.taskDefinition.containerDefinitions.head

    cd.logConfiguration shouldBe Some(LogConfiguration(
      logDriver = "awslogs",
      options = Map("awslogs-group" -> "awslogs-test", "awslogs-region" -> "eu-west-1")
    ))
  }

  it should "extract volumes configuration" in {
    val sc = ServiceConfig.read(Service("with-volumes"), cluster, Version.latest, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.containerDefinitions should have size 2
    val first = td.containerByName("first")
    val second = td.containerByName("second")

    first.volumesFrom shouldBe Nil
    second.volumesFrom shouldBe Seq(VolumeFrom(sourceContainer = "first", readOnly = false))
  }

  behavior of "taskRoleArn handling"

  it should "do not use role arn if missing" in {
    val sc = ServiceConfig.read(Service("bar"), cluster, Version.latest, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.taskRoleArn shouldBe None
  }

  it should "use a role arn if provided" in {
    val sc = ServiceConfig.read(Service("with-role"), cluster, Version.latest, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.taskRoleArn shouldEqual Some("arn:aws:iam::xxxxxxxxxxxx:role/test-task-role-arn")
  }

  it should "read links" in {
    val sc = ServiceConfig.read(Service("with-links"), cluster, Version.latest, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.containerDefinitions.head.links shouldEqual Seq("first", "second:alias")
  }

  behavior of "multiple containers"

  it should "extract multiple container definitions from list" in {
    val sc = ServiceConfig.read(Service("multiple-containers"), cluster, Version.latest, baseDir, defaultVars)
    val td = sc.taskDefinition

    td.containerDefinitions should have size 2
    val first +: second +: Nil = td.containerDefinitions

    first should have(
      'name("first"),
      'image(Image(Some("ecdc"), "node", Version.latest.value)),
      'memory(Some(1024)),
      'essential(true),
      'entryPoint(Seq("node")),
      'command(Seq("script.js")),
      'portMappings(Seq(PortMapping(containerPort = 9000)))
    )

    second should have(
      'name("second"),
      'image(Image(Some("ecdc"), "oracle-jre", "8")),
      'memory(Some(1024)),
      'essential(false),
      'entryPoint(Seq("java")),
      'command(Seq("-Dfoo.bar=yeah")),
      'portMappings(Seq(PortMapping(containerPort = 9001)))
    )
  }

  it should "extract multiple named container definitions stretching over service and trait" in {
    val sc = ServiceConfig.read(Service("named-container"), cluster, Version.latest, baseDir, defaultVars, Seq(DefaultServiceTrait("monitoring")))
    val td = sc.taskDefinition

    td.containerDefinitions should have size 2
    val second +: first +: Nil = td.containerDefinitions

    first should have(
      'name("first"),
      'image(Image(Some("ecdc"), "node", Version.latest.value)),
      'memory(Some(1024)),
      'essential(true),
      'entryPoint(Seq("node")),
      'command(Seq("script.js")),
      'portMappings(Seq(PortMapping(containerPort = 9000)))
    )

    second should have(
      'name("third"),
      'image(Image(Some("ecdc"), "oracle-jre", "8")),
      'memory(Some(1024)),
      'essential(false),
      'entryPoint(Seq("java")),
      'command(Seq("-Dfoo.bar=yeah")),
      'portMappings(Seq(PortMapping(containerPort = 9001)))
    )
  }

  it should "not extract memory" in {
    val sc = ServiceConfig.read(Service("without-memory"), cluster, Version.latest, baseDir, defaultVars)
    val cd = sc.taskDefinition.containerDefinitions.head

    cd should have(
      'memory(None)
    )
  }

  it should "extract memory reservation" in {
    val sc = ServiceConfig.read(Service("with-memory-reservation"), cluster, Version.latest, baseDir, defaultVars)
    val cd = sc.taskDefinition.containerDefinitions.head

    cd should have(
      'memoryReservation(Some(2611))
    )
  }
}
