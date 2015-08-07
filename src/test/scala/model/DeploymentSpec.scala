package model

import org.scalatest.{ FlatSpec, Matchers }
import play.api.libs.json.Json

class DeploymentSpec extends FlatSpec with Matchers {

  it should "write proper json" in {
    val dr = Deployment(Cluster("staging"), Service("foo"), Version("123"), Some(1))
    val json = Json toJson dr
    val expected = Json.obj("service" -> "foo",
      "cluster" -> "staging", "version" -> "123", "desiredCount" -> 1)
    json shouldBe expected
  }

  it should "read from json" in {
    val json = Json.obj("service" -> "foo", "cluster" -> "staging",
      "version" -> "123", "desiredCount" -> 1)
    val expected = Deployment(Cluster("staging"), Service("foo"), Version("123"), Some(1))
    val dr = json.as[Deployment]
    dr shouldBe expected
  }
}
