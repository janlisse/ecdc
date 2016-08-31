package ecdc.core

import java.io.File

import ecdc.core.TaskDef.PortMapping.{ Protocol, Tcp }
import ecdc.core.TaskDef._
import ecdc.core.TaskDef.ContainerDefinition.Image
import model.{ Cluster, Service, Version }

import scala.collection.JavaConverters._

import com.typesafe.config.{ Config, ConfigFactory }

case class ServiceConfig(taskDefinition: TaskDef, desiredCount: Option[Int], loadBalancer: Option[LoadBalancer])

case class LoadBalancer(instancePort: Int, loadBalancerPort: Int,
  protocol: String, instanceProtocol: String, scheme: String, serviceRole: String, subnets: Seq[String],
  securityGroups: Seq[String], healthCheck: HealthCheck)

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
    val containerDefinitions = (conf.getConfigSeq("containerDefinitions") match {
      case Nil => Seq(conf)
      case x => x
    }).map(cfg => ContainerDefinition(
      name = cfg.getStringOptional("name").getOrElse(service.name),
      image = {
        val imgConf = cfg.getConfig("image")
        Image(
          imgConf.getStringOptional("repositoryUrl"),
          imgConf.getStringOptional("name").getOrElse(service.name),
          imgConf.getStringOptional("version").getOrElse(version.value)
        )
      },
      cpu = cfg.getIntOptional("cpu"),
      memory = cfg.getInt("memory"),
      portMappings = cfg.getConfigSeq("portMappings").map(cfg =>
        PortMapping(
          cfg.getInt("containerPort"),
          cfg.getIntOptional("hostPort"),
          cfg.getOneOf("protocol", "udp", "tcp")
            .flatMap(Protocol.fromString).getOrElse(Tcp)
        )
      ),
      essential = cfg.getBooleanOptional("essential").getOrElse(true),
      entryPoint = cfg.getStringSeq("entryPoint"),
      command = cfg.getStringSeq("command"),
      environment = variables.map(v => Environment(v._1, v._2)).toSeq,
      mountPoints =
        cfg.getConfigSeq("mountPoints").map(cfg => {
          MountPoint(
            cfg.getString("sourceVolume"),
            cfg.getString("containerPath"),
            cfg.getBooleanOptional("readOnly").getOrElse(false)
          )
        }),
      ulimits =
        cfg.getConfigSeq("ulimits").map(cfg => {
          Ulimit(
            cfg.getString("name"),
            cfg.getInt("softLimit"),
            cfg.getInt("hardLimit")
          )
        }),
      logConfiguration = cfg.getConfigOptional("logConfiguration").map(cfg => {
        LogConfiguration(
          logDriver = cfg.getString("logDriver"),
          options = cfg.getConfig("options").entrySet().asScala.map(e => e.getKey -> e.getValue.unwrapped().toString).toMap
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
      LoadBalancer(instancePort, port, protocol, instanceProtocol, scheme, role, subnets, securityGroups, healthCheck)
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
