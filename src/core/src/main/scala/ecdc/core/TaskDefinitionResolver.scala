package ecdc
package core

import java.io.File

import ecdc.core.VariableResolver.{ EncryptedValue, PlainValue, Variable }
import ecdc.crypto.CmsDecryptor
import model.{ Cluster, Service, Version }

import scala.concurrent.{ ExecutionContext, Future }

trait TaskDefinitionResolver {
  def resolve(
    serviceConfig: ServiceConfig,
    baseDir: File,
    cluster: Cluster,
    service: Service,
    version: Version)(implicit ec: ExecutionContext): Future[TaskDef]
}

class FileSystemTaskDefinitionResolver()(implicit cmsDecryptor: CmsDecryptor) extends TaskDefinitionResolver {
  override def resolve(
    serviceConfig: ServiceConfig,
    baseDir: File,
    cluster: Cluster,
    service: Service,
    version: Version)(implicit ec: ExecutionContext): Future[TaskDef] = {

    val traits = TraitReader(service, baseDir).readTraits
    val vars = (VariableResolver.resolveVariables(baseDir, traits, service, cluster) +
      Variable("CLUSTER", PlainValue(cluster.name), "automatic") +
      Variable("SERVICE", PlainValue(service.name), "automatic") +
      Variable("VERSION", PlainValue(version.value), "automatic")).map(v => v.value match {
        case pv: PlainValue => (v.name, pv.content)
        case ev: EncryptedValue => (v.name, ev.content)
      }).toMap
    Future.successful(serviceConfig.readTaskDefinition(vars, traits))
  }
}
