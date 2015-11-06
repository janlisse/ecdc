package ecdc.core

import org.json4s._
import org.json4s.JsonAST.{ JNothing, JString }
import TaskDef._

case class TaskDef(
  family: String,
  containerDefinitions: Seq[ContainerDefinition],
  volumes: Seq[Volume] = Nil)

object TaskDef {

  import ContainerDefinition._
  import PortMapping._

  private object SeqSerializer extends Serializer[Seq[_]] {
    def deserialize(implicit format: Formats) = {
      case (TypeInfo(clazz, ptype), json) if classOf[Seq[_]].isAssignableFrom(clazz) => json match {
        case JArray(xs) =>
          val t = ptype.getOrElse(throw new MappingException("parameterized type not known"))
          xs.map(x => Extraction.extract(x, TypeInfo(t.getActualTypeArguments()(0).asInstanceOf[Class[_]], None))).toSeq
        case x => throw new MappingException(s"Can't convert $x to Seq")
      }
    }

    def serialize(implicit format: Formats) = {
      case i: Seq[_] if i.isEmpty => JNothing
      case i: Seq[_] => JArray(i.map(Extraction.decompose).toList)
    }
  }

  private object TaskDefFormats extends DefaultFormats {
    override val customSerializers: List[Serializer[_]] = List(SeqSerializer)
  }

  object Implicits {
    implicit val formats = TaskDefFormats + Protocol.Formats + Image.Formats
  }

  case class ContainerDefinition(
    name: String,
    image: Image,
    cpu: Option[Int] = None,
    memory: Int,
    links: Seq[String] = Nil,
    portMappings: Seq[PortMapping] = Nil,
    essential: Boolean = true,
    entryPoint: Seq[String] = Nil,
    command: Seq[String] = Nil,
    environment: Seq[Environment] = Nil,
    mountPoints: Seq[MountPoint] = Nil,
    volumesFrom: Seq[VolumeFrom] = Nil)

  object ContainerDefinition {
    case class Image(respositoryUrl: Option[String] = None, name: String, tag: String)
    object Image {
      object Formats extends CustomSerializer[Image](_ => (
        {
          case JString(s) => Image(None, "", "") // TODO deserialize?
        },
        {
          case Image(url, name, tag) => JString(url.map(_ + "/").getOrElse("") + s"$name:$tag")
        }
      ))
    }
  }

  case class PortMapping(containerPort: Int, hostPort: Option[Integer] = None, protocol: Protocol = Tcp)

  object PortMapping {
    sealed trait Protocol
    case object Tcp extends Protocol
    case object Udp extends Protocol

    object Protocol {
      object Formats extends CustomSerializer[Protocol](_ => (
        {
          case JString(s) if s == "tcp" => Tcp
          case JString(s) if s == "udp" => Udp
        },
        {
          case Tcp => JString("tcp")
          case Udp => JString("udp")
        }
      ))
    }
  }

  case class Environment(name: String, value: String)

  case class MountPoint(sourceVolume: String, containerPath: String, readOnly: Boolean = false)

  case class VolumeFrom(sourceContainer: String, readOnly: Boolean = false)

  case class Volume(name: String, host: Option[Host] = None)

  case class Host(sourcePath: Option[String] = None)
}
