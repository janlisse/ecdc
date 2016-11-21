package ecdc.core

import java.io.File

import ecdc.core.TaskDef.PortMapping.{ Protocol, Tcp }
import ecdc.core.TaskDef._
import ecdc.core.TaskDef.ContainerDefinition.Image
import model.{ Cluster, Service, Version }

import scala.collection.JavaConverters._
import com.typesafe.config._

case class ServiceConfig(taskDefinition: TaskDef, desiredCount: Option[Int], loadBalancer: Option[LoadBalancer])

case class LoadBalancer(
  instancePort: Int,
  loadBalancerPort: Int,
  protocol: String,
  instanceProtocol: String,
  scheme: String,
  serviceRole: String,
  subnets: Seq[String],
  securityGroups: Seq[String],
  healthCheck: HealthCheck,
  name: Option[String] = None)

case class HealthCheck(target: String, healthyThreshold: Int,
  unhealthyThreshold: Int, interval: Int, timeout: Int)

object ServiceConfig {

  def read(service: Service, cluster: Cluster, version: Version, repoDir: File,
    variables: Map[String, String], traits: Seq[ServiceTrait] = Seq.empty): ServiceConfig = {

    val conf = resolveConfig(service, cluster, repoDir, variables, traits)
    val taskDefinition = readTaskDef(conf, service, version, variables)
    val desiredCount = conf.getIntOptional("desiredCount")
    val loadBalancer = readLoadBalancer(conf)

    ServiceConfig(taskDefinition, desiredCount, loadBalancer)
  }

  private def readTaskDef(conf: Config, service: Service, version: Version, variables: Map[String, String]) = {
    val asConfig: ConfigValue => Config = c => c.atKey("t").getConfig("t")

    def buildImage(imgConf: Config): Image = {
      Image(
        imgConf.getStringOptional("repositoryUrl"),
        imgConf.getStringOptional("name").getOrElse(service.name),
        imgConf.getStringOptional("version").getOrElse(version.value)
      )
    }

    def buildPortMappings(portConf: Seq[Config]): Seq[PortMapping] = {
      portConf.map(cfg =>
        PortMapping(
          cfg.getInt("containerPort"),
          cfg.getIntOptional("hostPort"),
          cfg.getOneOf("protocol", "udp", "tcp")
            .flatMap(Protocol.fromString).getOrElse(Tcp)
        )
      )
    }

    def buildMountPoints(mountConf: Seq[Config]): Seq[MountPoint] = {
      mountConf.map(cfg => {
        MountPoint(
          cfg.getString("sourceVolume"),
          cfg.getString("containerPath"),
          cfg.getBooleanOptional("readOnly").getOrElse(false)
        )
      })
    }

    def buildUlimits(ulimitConf: Seq[Config]): Seq[Ulimit] = {
      ulimitConf.map(cfg => {
        Ulimit(
          cfg.getString("name"),
          cfg.getInt("softLimit"),
          cfg.getInt("hardLimit")
        )
      })
    }

    def buildVolumesFrom(volumesFromConf: Seq[Config]): Seq[VolumeFrom] = {
      volumesFromConf.map(cfg =>
        VolumeFrom(
          cfg.getString("sourceContainer"),
          cfg.getBooleanOptional("readonly").getOrElse(false)
        )
      )
    }

    def buildContainerDefinition(conf: (Config, String)): ContainerDefinition = {
      val cfg = conf._1
      val name = conf._2
      val containerVars = (variables ++ (cfg.getConfigOptional("environment") match {
        case None => Seq.empty
        case Some(x) => x.toMap
      })).map(v => Environment(v._1, v._2)).toSeq

      ContainerDefinition(
        loadbalanced = cfg.getBooleanOptional("loadbalanced").getOrElse(false),
        name = cfg.getStringOptional("name").getOrElse(name),
        image = buildImage(cfg.getConfig("image")),
        cpu = cfg.getIntOptional("cpu"),
        memory = cfg.getIntOptional("memory"),
        memoryReservation = cfg.getIntOptional("memoryReservation"),
        portMappings = buildPortMappings(cfg.getConfigSeq("portMappings")),
        essential = cfg.getBooleanOptional("essential").getOrElse(true),
        entryPoint = cfg.getStringSeq("entryPoint"),
        command = cfg.getStringSeq("command"),
        environment = containerVars,
        mountPoints = buildMountPoints(cfg.getConfigSeq("mountPoints")),
        ulimits = buildUlimits(cfg.getConfigSeq("ulimits")),
        volumesFrom = buildVolumesFrom(cfg.getConfigSeq("volumesFrom")),
        logConfiguration = cfg.getConfigOptional("logConfiguration").map(cfg => {
          LogConfiguration(
            logDriver = cfg.getString("logDriver"),
            options = cfg.getConfig("options").toMap
          )
        }),
        links = cfg.getStringSeq("links")
      )
    }

    val definitionsCfg = conf.getValueOptional("containerDefinitions") match {
      case None => Seq((conf, service.name))
      case Some(x) =>
        x match {
          case x: ConfigList => x.asScala.map((v) => (asConfig(v), service.name))
          case x: ConfigObject => x.entrySet().asScala.map(e => (asConfig(e.getValue), e.getKey)).toSeq
          case y => throw new RuntimeException(s"Can not handle config type ${x.getClass} for containerDefinitions")
        }
    }

    val containerDefinitions = definitionsCfg.map(buildContainerDefinition)
    val volumes = conf.getConfigSeq("volumes").map(
      volConfig => Volume(
        volConfig.getString("name"),
        volConfig.getConfigOptional("host")
          .map(hostConfig => hostConfig.getStringOptional("sourcePath"))
          .map(Host)
      )
    )
    val taskRole = conf.getStringOptional("taskRoleArn")

    TaskDef(service.name, containerDefinitions, volumes, taskRole)
  }

  private def resolveConfig(service: Service, cluster: Cluster, repoDir: File,
    variables: Map[String, String], traits: Seq[ServiceTrait]): Config = {

    val serviceConf = repoDir.toPath.resolve(s"service/${service.name}/service.conf").toFile
    val baseConfig = ConfigFactory.parseFile(serviceConf)
    val stackedConf = applyTraits(traits, baseConfig, repoDir, cluster)

    stackedConf.resolveWith(ConfigFactory.parseMap(variables.asJava, "config variables"))
  }

  def applyTraits(traits: Seq[ServiceTrait], baseConfig: Config, repoDir: File, cluster: Cluster) = {
    traits.foldLeft(baseConfig) {
      case (acc, t) =>
        val path = PathResolver.serviceConfFile(t, cluster)
        val confFile = new File(repoDir, path)
        if (confFile.exists()) {
          acc.withFallback(ConfigFactory.parseFile(confFile))
        } else acc
    }
  }

  private def readLoadBalancer(conf: Config): Option[LoadBalancer] = {
    conf.getConfigOptional("loadBalancer").map { lb =>
      val healthCheck = readHealthCheck(lb.getConfig("healthCheck"))
      val instancePort = lb.getInt("instancePort")
      val port = lb.getInt("port")
      val protocol = lb.getStringOptional("protocol").getOrElse("http")
      val instanceProtocol = lb.getStringOptional("instanceProtocol").getOrElse("http")
      val scheme = lb.getString("scheme")
      val subnets = lb.getStringList("subnets").asScala
      val securityGroups = lb.getStringList("securityGroups").asScala
      val role = lb.getString("serviceRole")
      val name = lb.getStringOptional("name")
      LoadBalancer(
        instancePort, port, protocol, instanceProtocol, scheme, role,
        subnets, securityGroups, healthCheck, name
      )
    }
  }

  private def readHealthCheck(conf: Config): HealthCheck = {
    val target = conf.getString("target")
    val healthyThreshold = conf.getInt("healthyThreshold")
    val unhealthyThreshold = conf.getInt("unhealthyThreshold")
    val interval = conf.getInt("interval")
    val timeout = conf.getInt("timeout")
    HealthCheck(target, healthyThreshold, unhealthyThreshold, interval, timeout)
  }
}
