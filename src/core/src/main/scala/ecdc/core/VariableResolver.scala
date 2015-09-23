package ecdc.core

import ecdc.core.TraitReader.ServiceTrait
import java.io.File
import model.Cluster
import scala.io.Source

object VariableResolver {

  case class Variable(name: String, value: String, path: String)

  def resolveVariables(baseDir: File, traits: Seq[ServiceTrait], cluster: Cluster): Set[Variable] = {
    traits.map {
      t =>
        val path = s"trait/${t.name}/cluster/${cluster.name}/var"
        val dir = new File(baseDir, path)
        Option(dir.list()).map(_.toSeq).getOrElse(Seq()).map(file => toVariable(new File(dir, file), path))
    }.toSet.flatten
  }

  private def toVariable(file: File, path: String): Variable = {
    val value = Source.fromFile(file).getLines.find(_ => true)
    Variable(file.getName, value.getOrElse(""), path)
  }
}
