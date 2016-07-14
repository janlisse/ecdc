package ecdc.core

import model.Cluster

object PathResolver {
  private def commonPath(t: ServiceTrait, cluster: Cluster): String = {
    t match {
      case DefaultServiceTrait(n) => s"trait/$n/cluster/${cluster.name}"
      case ServiceTraitWithFixedCluster(n, c) => s"trait/$n/cluster/${c.name}"
    }
  }
  def variableFolder(t: ServiceTrait, cluster: Cluster): String = {
    val cp = commonPath(t, cluster)

    s"$cp/var"
  }
  def serviceConfFile(t: ServiceTrait, cluster: Cluster): String = {
    val cp = commonPath(t, cluster)
    s"$cp/service.conf"
  }
}
