package ecdc.core

import java.io.File

import ecdc.crypto.{ EncryptionType, TextDecryptor }
import model.{ Cluster, Service }

import scala.io.Source

object VariableResolver {

  object Helper {
    def traitDirExists(baseDir: File, path: String): Boolean = {
      new File(baseDir, s"trait/$path").exists()
    }
    def traitDirExists(baseDir: File, path: String, cluster: Cluster): Boolean = {
      new File(baseDir, s"trait/$path/cluster/${cluster.name}").exists()
    }
  }

  case class Error(msg: String) extends AnyVal
  sealed trait Value
  case class PlainValue(content: String) extends Value
  case class EncryptedValue(cipherText: String, encryptionType: EncryptionType) extends Value {
    def content(implicit decryptor: TextDecryptor): String = {
      decryptor.decrypt(cipherText)
    }
  }

  case class Variable(name: String, value: Value, path: String) {
    override def equals(that: scala.Any): Boolean = {
      that match {
        case that: Variable => that.canEqual(this) && that.name == name
        case _ => false
      }
    }

    override def hashCode(): Int = 13 * name.hashCode

    override def canEqual(that: Any): Boolean = that.isInstanceOf[Variable]

  }

  def readFromPath(base: File, path: String): Set[Variable] = {
    val dir = new File(base, path)
    ls(dir).map(file => toVariable(new File(dir, file), path)).toSet
  }

  def resolveTraitVariables(baseDir: File, t: ServiceTrait, cluster: Cluster): Set[Variable] = {
    t match {
      case DefaultServiceTrait(n) => readFromPath(baseDir, s"trait/$n/cluster/${cluster.name}/var")
      case ServiceTraitWithFixedCluster(n, c) => readFromPath(baseDir, s"trait/$n/cluster/${c.name}/var")
    }
  }
  def resolveServiceVariables(baseDir: File, service: Service, cluster: Cluster): Set[Variable] = {
    readFromPath(baseDir, s"service/${service.name}/cluster/${cluster.name}/var")
  }
  def resolveVariables(baseDir: File, traits: Seq[ServiceTrait],
    service: Service, cluster: Cluster): Set[Variable] = {

    val serviceVars = resolveServiceVariables(baseDir, service, cluster)
    val traitVars = traits.flatMap(t => resolveTraitVariables(baseDir, t, cluster))
    (serviceVars ++ traitVars).toSet
  }

  private def ls(dir: File): Seq[String] = Option(dir.list()).map(_.toSeq).getOrElse(Seq())

  private val encR = """^ENC\[(.*?),(.*?)\]$""".r

  private def isEncrypted(s: String): Boolean = encR.findFirstMatchIn(s).isDefined
  private def unwrap(s: String): (EncryptionType, String) =
    encR.findFirstMatchIn(s)
      .map(m ⇒ EncryptionType(m.group(1)) -> m.group(2))
      .getOrElse(throw new IllegalArgumentException(s"Unable to unwrap encrypted value: $s"))

  private def toVariable(file: File, path: String): Variable = {
    val value = Source.fromFile(file).getLines().mkString("\n")
    val unwrapped = {
      if (isEncrypted(value)) {
        val unwrapped = unwrap(value)
        EncryptedValue(unwrapped._2, unwrapped._1)
      } else PlainValue(value)
    }
    Variable(file.getName, unwrapped, path)
  }
}
