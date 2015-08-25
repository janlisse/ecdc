package ecdc.api.json

import model.{Cluster, Deployment, Service, Version}
import org.scalatest.{Matchers, FlatSpec}
import play.api.libs.json.Json

class ReadsWritesSpec extends FlatSpec with Matchers {

  it should "read deployment from json" in {
    Json.obj(
      "cluster" -> "staging",
      "service" -> "foo",
      "version" -> "123",
      "desiredCount" -> 1
    ).as[Deployment] shouldBe Deployment(
      Cluster("staging"),
      Service("foo"),
      Version("123"),
      Some(1)
    )
  }

  it should "write deployment as json" in {
    Json toJson Deployment(
      Cluster("staging"),
      Service("foo"),
      Version("123"),
      Some(1)
    ) shouldBe Json.obj(
      "cluster" -> "staging",
      "service" -> "foo",
      "version" -> "123",
      "desiredCount" -> 1
    )
  }
}
