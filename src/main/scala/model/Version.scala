package model

import play.api.libs.json.{ JsString, JsValue, Writes }
import play.api.mvc.PathBindable

case class Version(value: String) extends AnyVal
object Version {
  implicit val writes: Writes[Version] = new Writes[Version] {
    override def writes(o: Version): JsValue = new JsString(o.value)
  }
  implicit val bindable: PathBindable[Version] = new PathBindable[Version] {
    override def unbind(key: String, value: Version): String = value.value
    override def bind(key: String, value: String): Either[String, Version] = Right(Version(value))
  }
  val latest: Version = Version("latest")
}
