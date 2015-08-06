package testutils

import org.scalatest._

trait Spec extends FlatSpec with ShouldMatchers with JsValueMatchers {}
