package ecdc.core

import java.io.File
import ecdc.core.TaskDef.PortMapping.{ Tcp, Protocol }
import ecdc.core.TaskDef._
import ecdc.core.TaskDef.ContainerDefinition.Image
import model.{ Cluster, Service }

import scala.collection.JavaConverters._

import com.typesafe.config.{ Config, ConfigFactory }

class ServiceConfig(service: Service, cluster: Cluster, repoDir: File) {

  val serviceConf = repoDir.toPath.resolve(s"service/${service.name}/service.conf").toFile

  def readTaskDefinition(variables: Map[String, String], traits: Seq[ServiceTrait]): TaskDef = {

    val baseConfig = ConfigFactory.parseFile(serviceConf)
    val stackedConf = applyTraits(traits, baseConfig)
    val conf = stackedConf.resolveWith(ConfigFactory.parseMap(variables.asJava, "config variables"))
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
          cfg.getInt("containerPort"),
          cfg.getIntOptional("hostPort"),
          cfg.getOneOf("protocol", "udp", "tcp")
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
    val desiredCount = conf.getIntOptional("desiredCount")
    TaskDef(service.name, containerDefinitions, volumes, desiredCount)
  }

  def applyTraits(traits: Seq[ServiceTrait], baseConfig: Config) = {
    traits.foldLeft(baseConfig) {
      case (acc, t) =>
        val path = s"trait/${t.name}/cluster/${cluster.name}/service.conf"
        val confFile = new File(repoDir, path)
        if (confFile.exists()) {
          acc.withFallback(ConfigFactory.parseFile(confFile))
        } else acc
    }
  }
}
