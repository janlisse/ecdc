package ecdc.core

import testutils.Spec
import ecdc.core.TaskDef.ContainerDefinition.Image
import ecdc.core.TaskDef.PortMapping.Udp
import org.json4s.native.Serialization.writePretty
import TaskDef._
import TaskDef.Implicits._

class TaskDefSpec extends Spec {

  it should "serialize a complete taskdef" in {
    writePretty(TaskDef(
      family = "abcFamily",
      containerDefinitions = Seq(ContainerDefinition(
        name = "abcName",
        image = Image(respositoryUrl = Some("quay.io"), name = "imageName", tag = "latest"),
        cpu = Some(5),
        memory = 1024,
        links = Seq("link"),
        portMappings = Seq(PortMapping(containerPort = 80, hostPort = Some(9000), protocol = Udp)),
        essential = false,
        entryPoint = Seq("/bin/entrypoint"),
        command = Seq("cmd"),
        environment = Seq(Environment(name = "PATH", value = "/bin")),
        mountPoints = Seq(MountPoint(sourceVolume = "/source", containerPath = "/container", readOnly = true)),
        ulimits = Seq(Ulimit(softLimit = 4096, hardLimit = 8192, name = "nofile")),
        volumesFrom = Seq(VolumeFrom(sourceContainer = "container", readOnly = true))
      )),
      volumes = Seq(Volume(name = "volumeName", host = Some(Host(sourcePath = Some("hostSourcePath")))))
    )) + "\n" shouldBe readFile("/ecdc/core/taskdef.complete.json").mkString
  }

  it should "serialize a minimal taskdef" in {
    writePretty(TaskDef(
      family = "123Family",
      containerDefinitions = Seq(ContainerDefinition(
        name = "123Name",
        image = Image(name = "anotherImage", tag = "1.1"),
        memory = 512
      ))
    )) + "\n" shouldBe readFile("/ecdc/core/taskdef.minimal.json").mkString
  }
}
