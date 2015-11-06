package testutils

import org.scalatest._

import scala.io.Source

trait Spec extends FlatSpec with ShouldMatchers with JsValueMatchers {
  def readFile(path: String): Source = Source.fromURL(getClass.getResource(path))
}
