package ecdc.core

import java.io.File
import ecdc.core.TaskDef.PortMapping.{ Tcp, Protocol }
import ecdc.core.TaskDef._
import ecdc.core.TaskDef.ContainerDefinition.Image
import model.Service

import scala.collection.JavaConverters._

import com.typesafe.config.{ Config, ConfigFactory }

case class ServiceTrait(name: String)

class ServiceConfig(service: Service, repoDir: File) {

  val serviceConf = repoDir.toPath.resolve(s"service/${service.name}/service.conf").toFile
  var extrapolatedConf: Option[Config] = None

  def readDesiredCount: Option[Int] = extrapolatedConf.map(_.getInt("desiredCount"))

  def readTraits: Seq[ServiceTrait] = ConfigFactory
    .parseFile(serviceConf)
    .getStringList("traits")
    .asScala
    .map(ServiceTrait)

  def readTaskDefinition(variables: Map[String, String]): TaskDef = {

    val conf = ConfigFactory.parseFile(serviceConf).resolveWith(
      ConfigFactory.parseMap(variables.asJava, "config variables")
    )
    extrapolatedConf = Some(conf)
    val containerDefinitions: Seq[ContainerDefinition] = Seq(ContainerDefinition(
      name = service.name,
      image = {
        val imgConf = conf.getConfig("image")
        Image(
          imgConf.getStringOptional("repositoryUrl"),
          imgConf.getStringOptional("name").getOrElse(service.name),
          imgConf.getStringOptional("tag").getOrElse("latest"))
      },
      cpu = conf.getIntOptional("cpu"),
      memory = conf.getInt("memory"),
      portMappings = conf.getConfigSeq("portMappings").map(cfg =>
        PortMapping(
          conf.getInt("containerPort"),
          conf.getIntOptional("hostPort"),
          conf.getOneOf("protocol", "udp", "tcp")
            .flatMap(Protocol.fromString).getOrElse(Tcp)
        )
      ),
      essential = true,
      entryPoint = conf.getStringSeq("entryPoint"),
      command = conf.getStringSeq("command"),
      environment = variables.map(v => Environment(v._1, v._2)).toSeq,
      mountPoints =
        conf.getConfigSeq("mountPoints").map(cfg => {
          MountPoint(
            cfg.getString("sourceVolume"),
            cfg.getString("containerPath"),
            cfg.getBooleanOptional("readOnly").getOrElse(false)
          )
        })
    ))
    val volumes = conf.getConfigSeq("volumes").map(
      volConfig => Volume(
        volConfig.getString("name"),
        volConfig.getConfigOptional("host")
          .map(hostConfig => hostConfig.getStringOptional("sourcePath"))
          .map(Host)
      )
    )
    TaskDef(service.name, containerDefinitions, volumes)
  }
}
