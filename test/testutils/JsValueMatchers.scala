package testutils

import org.scalactic.Prettifier
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.libs.json._
import scala.io.Codec.UTF8
import scala.io.Source
import JsValueMatchers._

import scala.util.Try

trait JsValueMatchers {

  implicit val defaultCodec = UTF8

  def beJson(res: String) = new Matcher[JsValue] {
    override def apply(left: JsValue): MatchResult = matchResult(left, synchronized { asJson(res) })
  }

  def matchResult(left: JsValue, right: JsValue) = MatchResult(
    left equals right,
    "{0}",
    "{0}",
    "{0}",
    "{0}",
    IndexedSeq("failure"),
    IndexedSeq("negatedFailure"),
    IndexedSeq("midSentenceFailure"),
    IndexedSeq("midSentenceNegatedFailure"),
    new Pretty(diff(left, right).mkString("\n"), "JsValue was equal to expected.")
  )

  class Pretty(failure: => String, negFailure: => String) extends Prettifier {
    override def apply(a: Any): String = a match {
      case "failure" => failure
      case "negatedFailure" => negFailure
      case "midSentenceFailure" => failure
      case "midSentenceNegatedFailure" => negFailure
    }
  }

  def beJsValue(right: JsValue) = new Matcher[JsValue] {
    override def apply(left: JsValue): MatchResult = matchResult(left, right)
  }

  def findResource(s: String) = Option(getClass getResource s) match {
    case Some(r) => r
    case _ => throw new IllegalArgumentException(s"could not find $s")
  }

  def asJson(res: String) = Json parse (Source fromURL findResource(s"/$res")).mkString

  def diff(left: JsValue, right: JsValue, root: List[String] = Nil, acc: List[String] = Nil): List[String] = (left, right) match {
    case (JsNull, JsNull)                           => acc
    case (JsBoolean(a), JsBoolean(b)) if a equals b => acc
    case (JsBoolean(a), JsBoolean(b))               => toString((root, a, b)) :: acc
    case (JsNumber(a), JsNumber(b))   if a equals b => acc
    case (JsNumber(a), JsNumber(b))                 => toString((root, a, b)) :: acc
    case (JsString(a), JsString(b))   if a equals b => acc
    case (JsString(a), JsString(b))                 => toString((root, a, b)) :: acc
    case (JsArray(a), JsArray(b))                   => diffJsValues(a, b, root)
    case (JsObject(a), JsObject(b))                 => diffJsObjects(a.seq.toSeq, b.seq.toSeq, root)
    case (a, b)                                     => toString((root, a, b)) :: acc
  }

  def diffJsObjects(a: Seq[(String, JsValue)], b: Seq[(String, JsValue)], root: List[String] = Nil, acc: List[String] = Nil): List[String] = {

    if (a.size != b.size) {
      val as = a.map(_._1).toSet
      val bs = b.map(_._1).toSet
      (as.filter(ax => !bs.contains(ax)).map("'" + _ + "' is too much") ++
        bs.filter(bx => !as.contains(bx)).map("'" + _ + "' is missing")).toList
    } else {
      val as = a sortWith (_._1 > _._1)
      val bs = b sortWith (_._1 > _._1)
      as.zip(bs).foldLeft(List.empty[String]) {

        (acc, elem) => elem match {
          case ((ls, ljs), (rs, rjs)) if ls == rs && ljs == rjs =>
            acc
          case ((ls, ljs), (rs, rjs)) if ls == rs =>
            diff(ljs, rjs, root ++ List(ls), acc)
          case ((ls, ljs), (rs, rjs)) =>
            toString(root, ls, rs) :: acc
        }
      }
    }
  }

  def diffJsValues(a: Seq[JsValue], b: Seq[JsValue], root: List[String] = Nil, acc: List[String] = Nil): List[String] = {
    a.zipAll(b, JsNull, JsNull).foldLeft(List.empty[String]) {
      (acc, elem) => diff(elem._1, elem._2, root) ++ acc
    }
  }

  def toString(t: (List[String], Any, Any)) = t match {
    case (Nil, a, b) => s"'$a' was not '$b'"
    case (xs, a, b) => xs.mkString(".") + s": '$a' was not '$b'"
  }
}

object JsValueMatchers extends JsValueMatchers {
  private implicit class JsonAwareList(xs: List[String]) {
    def jsonString = xs match {
      case Nil => "'root'"
      case _ => xs.mkString(".")
    }
  }
}
