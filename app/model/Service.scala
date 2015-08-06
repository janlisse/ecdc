package model

import play.api.libs.json.{JsString, JsValue, Writes}
import play.api.mvc.PathBindable

case class Service(name: String) extends AnyVal
object Service {
  implicit val writes: Writes[Service] = new Writes[Service] {
    override def writes(o: Service): JsValue = new JsString(o.name)
  }
  implicit val bindable: PathBindable[Service] = new PathBindable[Service] {
    override def unbind(key: String, value: Service): String = value.name
    override def bind(key: String, value: String): Either[String, Service] = Right(Service(value))
  }
}
