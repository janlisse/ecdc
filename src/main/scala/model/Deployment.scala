package model

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Deployment(cluster: Cluster, service: Service, version: Version, desiredCount: Option[Int])

object Deployment {

  implicit val reads: Reads[Deployment] = (
    (__ \ 'cluster).read[String].map(Cluster.apply) and
    (__ \ "service").read[String].map(Service.apply) and
    (__ \ 'version).read[String].map(Version.apply) and
    (__ \ 'desiredCount).readNullable[Int]
  )(Deployment.apply _)

  implicit val writes: Writes[Deployment] = (
    (__ \ 'cluster).write[Cluster] and
    (__ \ "service").write[Service] and
    (__ \ 'version).write[Version] and
    (__ \ 'desiredCount).writeNullable[Int]
  )(unlift(Deployment.unapply))
}
