package ecdc.api

import model.{ Version, Service, Cluster }
import play.api.mvc.PathBindable

package object bindables {

  implicit val clusterPathBindable: PathBindable[Cluster] = new PathBindable[Cluster] {
    override def unbind(key: String, value: Cluster): String = value.name
    override def bind(key: String, value: String): Either[String, Cluster] = Right(Cluster(value))
  }

  implicit val serviceBindable: PathBindable[Service] = new PathBindable[Service] {
    override def unbind(key: String, value: Service): String = value.name
    override def bind(key: String, value: String): Either[String, Service] = Right(Service(value))
  }

  implicit val versionBindable: PathBindable[Version] = new PathBindable[Version] {
    override def unbind(key: String, value: Version): String = value.value
    override def bind(key: String, value: String): Either[String, Version] = Right(Version(value))
  }
}
