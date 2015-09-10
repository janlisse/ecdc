package ecdc.core

import ecdc.core.TraitReader.ServiceTrait
import model.Cluster

object VariableResolver {

  case class Variable(name: String, value: String, path: String)

  def resolveVariables(traits: Seq[ServiceTrait], cluster: Cluster): Set[Variable] = ???
}
