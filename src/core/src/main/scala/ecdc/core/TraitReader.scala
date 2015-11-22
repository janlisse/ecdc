package ecdc.core

import java.io.File
import java.util

import com.typesafe.config.ConfigFactory
import model.Service
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import scala.util.Try
import TraitReader._

case class ServiceTrait(name: String)

case class TraitReader(service: Service, repoDir: File) {

  val traitConf = repoDir.toPath.resolve(s"service/${service.name}/trait.conf").toFile

  def readTraits: Seq[ServiceTrait] = {
    Try(ConfigFactory
      .parseFile(traitConf)
      .getStringList("traits")).getOrElse(new util.ArrayList())
      .asScala
      .filter(traitDirectoryExists(_, repoDir))
      .map(ServiceTrait)
  }
}

object TraitReader {

  val logger = LoggerFactory.getLogger(getClass)

  def traitDirectoryExists(t: String, repoDir: File): Boolean =
    if (new File(repoDir, s"trait/$t").exists()) true
    else {
      logger.warn(s"Unable to read trait directory: trait/$t")
      false
    }

}
