package ecdc.core

import java.io.File
import ecdc.core.TaskDef.PortMapping.{ Tcp, Protocol }
import ecdc.core.TaskDef._
import ecdc.core.TaskDef.ContainerDefinition.Image
import model.{ Cluster, Service }

import scala.collection.JavaConverters._

import com.typesafe.config.{ Config, ConfigFactory }

case class ServiceConfig(taskDefinition: TaskDef, desiredCount: Option[Int], loadBalancer: Option[LoadBalancer])
case class LoadBalancer(instancePort: Int, loadBalancerPort: Int,
  protocol: String, instanceProtocol: String, scheme: String, serviceRole: String, subnets: Seq[String],
  securityGroups: Seq[String], healthCheck: HealthCheck)
case class HealthCheck(target: String, healthyThreshold: Int,
  unhealthyThreshold: Int, interval: Int, timeout: Int)

object ServiceConfig {

  def read(service: Service, cluster: Cluster, repoDir: File,
    variables: Map[String, String], traits: Seq[ServiceTrait]): ServiceConfig = {

    val conf = resolveConfig(service, cluster, repoDir, variables, traits)
    val taskDefinition = readTaskDef(conf, service, variables)
    val desiredCount = conf.getIntOptional("desiredCount")
    val loadBalancer = readLoadBalancer(conf)
    ServiceConfig(taskDefinition, desiredCount, loadBalancer)
  }

  private def readTaskDef(conf: Config, service: Service, variables: Map[String, String]) = {
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
    TaskDef(service.name, containerDefinitions, volumes)
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
        val path = s"trait/${t.name}/cluster/${cluster.name}/service.conf"
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
