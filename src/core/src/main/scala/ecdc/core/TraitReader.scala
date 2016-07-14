package ecdc.core

import java.io.File
import java.util
import java.util.regex.Pattern

import com.typesafe.config.ConfigFactory
import model.{ Cluster, Service }
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try
import TraitReader._
import ecdc.core.VariableResolver.Helper.traitDirExists

trait ServiceTrait {
  def name: String
}

case class DefaultServiceTrait(name: String) extends ServiceTrait
case class ServiceTraitWithFixedCluster(name: String, cluster: Cluster) extends ServiceTrait

case class TraitReader(service: Service, repoDir: File) {

  val traitConf = repoDir.toPath.resolve(s"service/${service.name}/trait.conf").toFile

  def readTraits: Seq[ServiceTrait] = {
    Try(ConfigFactory
      .parseFile(traitConf)
      .getStringList("traits")).getOrElse(new util.ArrayList())
      .asScala
      .flatMap(getServiceTrait(_, repoDir))
  }
}

object TraitReader {

  val logger = LoggerFactory.getLogger(getClass)
  def getServiceTrait(t: String, repoDir: File): Option[ServiceTrait] = {
    t.split(Pattern.quote("->"), 2).toList match {
      case n :: Nil if traitDirExists(repoDir, n) => Some(DefaultServiceTrait(n))
      case n :: c :: Nil if traitDirExists(repoDir, n, Cluster(c)) => Some(ServiceTraitWithFixedCluster(n, Cluster(c)))
      case _ =>
        logger.warn(s"Unable to read trait directory: $t")
        None
    }
  }
}
