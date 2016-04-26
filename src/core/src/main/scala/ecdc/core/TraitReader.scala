package ecdc.core

import java.io.File
import java.util

import com.typesafe.config.ConfigFactory
import model.{ Cluster, Service }
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try
import TraitReader._

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
    val serviceTrait = t.split("->", 2).toList match {
      case n :: Nil =>
        VariableResolver.Helper.traitDirExists(repoDir, n) match {
          case true => Some(DefaultServiceTrait(n))
          case false => None
        }
      case n :: c :: Nil =>
        val cluster = Cluster(c)
        VariableResolver.Helper.traitDirExists(repoDir, n, cluster) match {
          case true => Some(ServiceTraitWithFixedCluster(n, cluster))
          case false => None
        }
      case default => None
    }

    serviceTrait match {
      case None =>
        logger.warn(s"Unable to read trait directory: $t")
      case _ =>
    }

    serviceTrait
  }
}
