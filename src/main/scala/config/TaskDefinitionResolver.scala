package config

import java.io.File

import crypto.{ CmsDecryptor, EncryptionType }
import model.Cluster
import org.slf4j.LoggerFactory
import play.api.libs.json.{ Json, JsObject }
import TaskDefinitionResolver._
import scala.io.Source
import scala.language.reflectiveCalls
import scala.util.{ Success, Failure, Try }

trait Arm {
  def using[T <: { def close() }, B](resource: T)(block: T ⇒ B): B = {
    try {
      block(resource)
    } finally {
      if (resource != null) resource.close()
    }
  }
}

sealed trait Value
case class PlainValue(content: String) extends Value
case class EncryptedValue(cipherText: String, encryptionType: EncryptionType) extends Value

case class Variable(name: String, file: File) extends Arm {

  val encR = """^ENC\[(.*?),(.*?)\]$""".r
  val value: Value = {
    using(Source.fromFile(file)) {
      s ⇒
        {
          val value = s.getLines().mkString("\n")
          if (isEncrypted(value)) {
            val unwrapped = unwrap(value)
            EncryptedValue(unwrapped._2, unwrapped._1)
          } else PlainValue(value)
        }
    }
  }

  override def toString: String = {
    s"$name: $value"
  }
  private def isEncrypted(s: String): Boolean = encR.findFirstMatchIn(s).isDefined
  private def unwrap(s: String): (EncryptionType, String) =
    encR.findFirstMatchIn(s)
      .map(m ⇒ EncryptionType(m.group(1)) -> m.group(2))
      .getOrElse(throw new IllegalArgumentException(s"Unable to unwrap encrypted value: $s"))
}

case class Template(file: File, context: Map[String, String]) extends Arm {
  val tmpl = using(Source.fromFile(file))(_.getLines().mkString("\n"))
  def render: String = {
    """\$\{(\w+)\}""".r.replaceAllIn(tmpl, m ⇒ {
      context.getOrElse(m.group(1),
        throw new IllegalArgumentException(s"Template variable not defined: ${m.group(1)}"))
    })
  }
}

trait TaskDefinitionResolver {
  def resolve(baseDir: File, app: String, env: Cluster,
              additionalVars: Map[String, String],
              taskdefFileName: String = "taskdef.json"): Either[Seq[Error], JsObject]
}

object TaskDefinitionResolver {
  case class Error(msg: String) extends AnyVal
}

class FileSystemTaskDefinitionResolver(cmsDecryptor: CmsDecryptor) extends TaskDefinitionResolver {

  val logger = LoggerFactory.getLogger(getClass)

  def resolveVariables(baseDir: File, app: String, cluster: Cluster): Set[Variable] = {
    //TODO get hierarchies from config
    val hierarchy = Seq("cluster", s"cluster/${cluster.name}", s"service/$app", s"service/$app/cluster/${cluster.name}")
    hierarchy.map {
      d ⇒
        val f = new File(baseDir, s"$d/var")
        logger.debug(s"Read variables from: $f")
        val vars = Option(f.list()).map(_.toSeq).getOrElse(Seq())
        logger.debug(s"Found variables: ${vars.mkString(",")}")
        vars.map(f1 ⇒ toVariable(f, f1))
    }.foldLeft(Set[Variable]()) {
      (acc, el) ⇒
        {
          val filtered = acc.filterNot(v ⇒ el.map(_.name).contains(v.name))
          filtered ++ el.toSet
        }
    }
  }

  override def resolve(baseDir: File, app: String, cluster: Cluster, additionalVars: Map[String, String] = Map(),
                       taskDefFileName: String = "taskdef.json"): Either[Seq[Error], JsObject] = {

    def template(maybeF: Either[Seq[Error], File], vars: Set[Variable]): Either[Seq[Error], Template] = {
      maybeF.right.flatMap(
        f ⇒ {
          val extractedVars: Set[Either[Seq[Error], (String, String)]] = vars.map(v ⇒ extract(v.value)
            .right.map(value ⇒ v.name -> value)
            .left.map(es ⇒ es.map(e ⇒ Error(e.msg + ": " + v.name))))
          val allVars = extractedVars.foldLeft(Right(Map.empty[String, String]): Either[Seq[Error], Map[String, String]]) {
            (acc, elem) ⇒ elem.right.flatMap { case (key, value) ⇒ acc.right.map(_ + (key -> value)) }
          }.right.map(x ⇒ x ++ additionalVars)
          allVars.right.map(context ⇒ Template(f, context))
        }
      )
    }

    //TODO get hierarchies from config
    val hierarchy = Seq("cluster", s"cluster/${cluster.name}", s"service/$app", s"service/$app/cluster/${cluster.name}")
    val mayBeF = hierarchy.map(d ⇒ new File(baseDir, s"$d/$taskDefFileName"))
      .filter(_.exists())
      .lastOption
      .toRight(Seq(Error(s"no '$taskDefFileName' in hierarchy found")))
    logger.debug(s"Resolved template: $mayBeF")
    val resolvedVars: Set[Variable] = resolveVariables(baseDir, app, cluster)
    logger.debug(s"Resolved vars: ${resolvedVars.mkString("\n")}")
    template(mayBeF, resolvedVars).right.flatMap {
      t ⇒
        Try { Json.parse(t.render).as[JsObject] } match {
          case Failure(err) ⇒ Left(Seq(Error(err.getMessage)))
          case Success(x)   ⇒ Right(x)
        }
    }
  }

  private def extract(v: Value): Either[Seq[Error], String] = {
    v match {
      case PlainValue(x) ⇒ Right(x)
      case EncryptedValue(c, et) ⇒ Try{ cmsDecryptor.decrypt(et, c) } match {
        case Success(dec) ⇒ Right(dec)
        case Failure(err) ⇒ Left(Seq(Error("error decrypting value")))
      }
    }
  }

  private def toVariable(dir: File, s: String): Variable = {
    val v = new File(dir, s)
    Variable(v.getName, v)
  }
}
