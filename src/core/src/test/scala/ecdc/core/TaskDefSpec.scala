package ecdc.core

import ecdc.core.TaskDef.ContainerDefinition.Image
import ecdc.core.TaskDef.PortMapping.Udp
import org.scalatest.{ ShouldMatchers, FlatSpec }
import org.json4s.native.Serialization.write
import TaskDef._
import TaskDef.Implicits._

class TaskDefSpec extends FlatSpec with ShouldMatchers {

  it should "serialize a complete taskDef" in {
    write(TaskDef(
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
        volumesFrom = Seq(VolumeFrom(sourceContainer = "container", readOnly = true))
      )),
      volumes = Seq(Volume(name = "volumeName", host = Some(Host(sourcePath = Some("hostSourcePath")))))
    )) shouldBe """{"family":"abcFamily","containerDefinitions":[{"name":"abcName","image":"quay.io/imageName:latest","cpu":5,"memory":1024,"links":["link"],"portMappings":[{"containerPort":80,"hostPort":9000,"protocol":"udp"}],"essential":false,"entryPoint":["/bin/entrypoint"],"command":["cmd"],"environment":[{"name":"PATH","value":"/bin"}],"mountPoints":[{"sourceVolume":"/source","containerPath":"/container","readOnly":true}],"volumesFrom":[{"sourceContainer":"container","readOnly":true}]}],"volumes":[{"name":"volumeName","host":{"sourcePath":"hostSourcePath"}}]}"""
  }

  ignore should "serialize a small taskDef" in {
    write(TaskDef(
      family = "123Family",
      containerDefinitions = Seq(ContainerDefinition(
        name = "123Name",
        image = Image(name = "anotherImage", tag = "1.1"),
        memory = 512
      ))
    )) shouldBe """{"family":"123Family","containerDefinitions":[{"name":"123Name","image":"anotherImage:1.1","memory":512,"essential":true}]}"""
  }
}
