package ecdc.core

import config.Arm
import TraitReader.ServiceTrait
import java.io.File
import model.Cluster
import scala.io.Source

object VariableResolver extends Arm {

  case class Variable(name: String, value: String, path: String) {
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

  private def toVariable(file: File, path: String): Variable = {
    val value = using(Source.fromFile(file))(_.getLines.find(_ => true))
    Variable(file.getName, value.getOrElse(""), path)
  }
}
