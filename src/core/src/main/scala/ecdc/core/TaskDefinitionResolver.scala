package ecdc
package core

import java.io.File
import com.typesafe.config.ConfigFactory
import ecdc.core.TaskDef.PortMapping.{ Tcp, Protocol }
import ecdc.core.TaskDef._
import ecdc.core.TaskDef.ContainerDefinition.Image
import ecdc.core.VariableResolver.{ EncryptedValue, PlainValue, Variable }
import ecdc.crypto.CmsDecryptor
import model.{ Version, Service, Cluster }
import scala.concurrent.{ Future, ExecutionContext }
import scala.collection.JavaConverters._

trait TaskDefinitionResolver {
  def resolve(baseDir: File,
    cluster: Cluster,
    service: Service,
    version: Version)(implicit ec: ExecutionContext): Future[TaskDef]
}

class FileSystemTaskDefinitionResolver()(implicit cmsDecryptor: CmsDecryptor) extends TaskDefinitionResolver {
  override def resolve(
    baseDir: File,
    cluster: Cluster,
    service: Service,
    version: Version)(implicit ec: ExecutionContext): Future[TaskDef] = {

    val serviceConf = baseDir.toPath.resolve(s"service/${service.name}/service.conf").toFile
    val serviceTraits = TraitReader.readTraits(serviceConf)
    val vars = (VariableResolver.resolveVariables(baseDir, serviceTraits, cluster) +
      Variable("CLUSTER", PlainValue(cluster.name), "automatic") +
      Variable("SERVICE", PlainValue(service.name), "automatic") +
      Variable("VERSION", PlainValue(version.value), "automatic")).map(v => v.value match {
        case pv: PlainValue => (v.name, pv.content)
        case ev: EncryptedValue => (v.name, ev.content)
      }).toMap
    val conf = ConfigFactory.parseFile(serviceConf).resolveWith(
      ConfigFactory.parseMap(vars.asJava, "config variables")
    )
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
      environment = vars.map(v => Environment(v._1, v._2)).toSeq,
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
    Future.successful(TaskDef(service.name, containerDefinitions, volumes))
  }
}
