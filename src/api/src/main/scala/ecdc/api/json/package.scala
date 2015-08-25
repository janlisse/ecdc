package ecdc.api

import model.{ Cluster, Deployment, Service, Version }
import play.api.libs.functional.syntax._
import play.api.libs.json._

package object json {

  implicit val clusterReads: Reads[Cluster] = new Reads[Cluster] {
    override def reads(js: JsValue): JsResult[Cluster] = read(js) {
      case JsString(x) => Cluster(x)
    }
  }

  implicit val clusterWrites: Writes[Cluster] = new Writes[Cluster] {
    override def writes(o: Cluster): JsValue = new JsString(o.name)
  }

  implicit val serviceReads: Reads[Service] = new Reads[Service] {
    override def reads(js: JsValue): JsResult[Service] = read(js) {
      case JsString(x) => Service(x)
    }
  }

  implicit val serviceWrites: Writes[Service] = new Writes[Service] {
    override def writes(o: Service): JsValue = new JsString(o.name)
  }

  implicit val versionReads: Reads[Version] = new Reads[Version] {
    override def reads(js: JsValue): JsResult[Version] = read(js) {
      case JsString(x) => Version(x)
    }
  }

  implicit val versionWrites: Writes[Version] = new Writes[Version] {
    override def writes(o: Version): JsValue = new JsString(o.value)
  }

  implicit val deploymentReads: Reads[Deployment] = (
    (__ \ 'cluster).read[Cluster] and
    (__ \ 'service).read[Service] and
    (__ \ 'version).read[Version] and
    (__ \ 'desiredCount).readNullable[Int]
  )(Deployment.apply _)

  implicit val deploymentWrites: Writes[Deployment] = (
    (__ \ 'cluster).write[Cluster] and
    (__ \ 'service).write[Service] and
    (__ \ 'version).write[Version] and
    (__ \ 'desiredCount).writeNullable[Int]
  )(unlift(Deployment.unapply))

  private def read[T](js: JsValue)(f: PartialFunction[JsValue, T]): JsResult[T] =
    if (f isDefinedAt js) JsSuccess(f(js))
    else JsError(s"js value: $js cannot be parsed. Wrong type.")
}
