package ecdc.core

import testutils.Spec
import ecdc.core.TaskDef.ContainerDefinition.Image
import ecdc.core.TaskDef.PortMapping.{ Udp, Tcp }
import org.json4s.native.Serialization.writePretty
import TaskDef._
import TaskDef.Implicits._

class TaskDefSpec extends Spec {

  val abcContainer = ContainerDefinition(
    loadbalanced = false,
    name = "abcName",
    image = Image(respositoryUrl = Some("quay.io"), name = "imageName", tag = "latest"),
    cpu = Some(5),
    memory = Some(1024),
    links = Seq("link"),
    portMappings = Seq(PortMapping(containerPort = 80, hostPort = Some(9000), protocol = Udp)),
    essential = false,
    entryPoint = Seq("/bin/entrypoint"),
    command = Seq("cmd"),
    environment = Seq(Environment(name = "PATH", value = "/bin")),
    mountPoints = Seq(MountPoint(sourceVolume = "/source", containerPath = "/container", readOnly = true)),
    ulimits = Seq(Ulimit(softLimit = 4096, hardLimit = 8192, name = "nofile")),
    volumesFrom = Seq(VolumeFrom(sourceContainer = "container", readOnly = true)),
    logConfiguration = Some(LogConfiguration(logDriver = "awslogs", options = Map("awslogs-group" -> "awslogs-test", "awslogs-region" -> "eu-west-1")))
  )

  val xyzContainer = ContainerDefinition(
    name = "xyzName",
    image = Image(respositoryUrl = Some("quay.io"), name = "otherImageName", tag = "1.2.3"),
    cpu = Some(10),
    memory = Some(512),
    links = Seq("other-link"),
    portMappings = Seq(PortMapping(containerPort = 80, hostPort = Some(8000), protocol = Tcp)),
    essential = false,
    entryPoint = Seq("/bin/entrypoint"),
    command = Seq("arg1", "arg2"),
    mountPoints = Seq(MountPoint(sourceVolume = "/source", containerPath = "/container", readOnly = true)),
    ulimits = Seq(Ulimit(softLimit = 4096, hardLimit = 8192, name = "nofile")),
    volumesFrom = Seq(VolumeFrom(sourceContainer = "container", readOnly = true))
  )

  it should "serialize a complete taskdef" in {
    writePretty(TaskDef(
      family = "abcFamily",
      containerDefinitions = Seq(abcContainer),
      volumes = Seq(Volume(name = "volumeName", host = Some(Host(sourcePath = Some("hostSourcePath"))))),
      taskRoleArn = Some("arn:aws:iam::xxxxxxxxxxxx:role/test-task-role-arn")
    )) + "\n" shouldBe readFile("/ecdc/core/taskdef.complete.json").mkString
  }

  it should "serialize a minimal taskdef" in {
    writePretty(TaskDef(
      family = "123Family",
      containerDefinitions = Seq(ContainerDefinition(
        name = "123Name",
        image = Image(name = "anotherImage", tag = "1.1"),
        memory = Some(512)
      ))
    )) + "\n" shouldBe readFile("/ecdc/core/taskdef.minimal.json").mkString
  }

  it should "serialize a taskdef with multiple containers" in {
    writePretty(TaskDef(
      family = "abcFamily",
      containerDefinitions = Seq(abcContainer, xyzContainer),
      volumes = Seq(Volume(name = "volumeName", host = Some(Host(sourcePath = Some("hostSourcePath"))))),
      taskRoleArn = Some("arn:aws:iam::xxxxxxxxxxxx:role/test-task-role-arn")
    )) + "\n" shouldBe readFile("/ecdc/core/taskdef.multiple-containers.json").mkString
  }
}
