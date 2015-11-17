package ecdc.core

import java.io.File

import com.typesafe.config.ConfigFactory
import model.Service
import scala.collection.JavaConverters._

case class ServiceTrait(name: String)

case class TraitReader(service: Service, repoDir: File) {

  val traitConf = repoDir.toPath.resolve(s"service/${service.name}/traits.conf").toFile

  def readTraits: Seq[ServiceTrait] = ConfigFactory
    .parseFile(traitConf)
    .getStringList("traits")
    .asScala
    .map(ServiceTrait)
}
