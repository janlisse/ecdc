package ecdc.core

import java.io.File

import ecdc.core.TraitReader.ServiceTrait
import ecdc.crypto.{ CmsDecryptor, EncryptionType }
import model.Cluster

import scala.io.Source

object VariableResolver {

  case class Error(msg: String) extends AnyVal
  sealed trait Value
  case class PlainValue(content: String) extends Value
  case class EncryptedValue(cipherText: String, encryptionType: EncryptionType) extends Value {
    def content(implicit cmsDecryptor: CmsDecryptor): String = {
      cmsDecryptor.decrypt(cipherText)
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

  def resolveVariables(baseDir: File, traits: Seq[ServiceTrait], cluster: Cluster): Set[Variable] = {
    traits.flatMap {
      t =>
        val path = s"trait/${t.name}/cluster/${cluster.name}/var"
        val dir = new File(baseDir, path)
        ls(dir).map(file => toVariable(new File(dir, file), path))
    }.toSet
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
