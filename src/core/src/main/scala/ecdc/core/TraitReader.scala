package ecdc.core

import java.io.File
import java.util

import com.typesafe.config.ConfigFactory
import model.Service
import scala.collection.JavaConverters._
import scala.util.Try

case class ServiceTrait(name: String)

case class TraitReader(service: Service, repoDir: File) {

  val traitConf = repoDir.toPath.resolve(s"service/${service.name}/traits.conf").toFile

  def readTraits: Seq[ServiceTrait] = {
    Try(ConfigFactory
      .parseFile(traitConf)
      .getStringList("traits")).getOrElse(new util.ArrayList())
      .asScala
      .map(ServiceTrait)
  }
}
