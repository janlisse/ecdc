package model

import play.api.libs.json.{JsString, JsValue, Writes}
import play.api.mvc.PathBindable

case class Cluster(name: String) extends AnyVal
object Cluster {
  implicit val writes: Writes[Cluster] = new Writes[Cluster] {
    override def writes(o: Cluster): JsValue = new JsString(o.name)
  }
  implicit val bindable: PathBindable[Cluster] = new PathBindable[Cluster] {
    override def unbind(key: String, value: Cluster): String = value.name
    override def bind(key: String, value: String): Either[String, Cluster] = Right(Cluster(value))
  }
}
