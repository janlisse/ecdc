package ecdc.core

import ecdc.core.VariableResolver._
import java.io.File
import model.{ Service, Cluster }
import testutils.Spec

class VariableResolverSpec extends Spec {

  val testRepo = new File("./src/core/src/test/resources/testRepo")

  it should "resolve variables for traits" in {
    val vars = resolveVariables(testRepo,
      Seq(DefaultServiceTrait("logging"), DefaultServiceTrait("syslog")), Service("foo"), Cluster("production"))
    vars should have size 2
    vars should contain(Variable("LOGGING_SERVICE_KEY",
      PlainValue("logging_service_key_foo"), "trait/logging/cluster/production/var"))
  }

  it should "pick service vars in favour of trait vars" in {
    val vars = resolveVariables(testRepo,
      Seq(DefaultServiceTrait("syslog")), Service("foo"), Cluster("production"))
    vars.head shouldBe Variable("SYSLOG_URL",
      PlainValue("syslog_url_foo"), "service/foo/cluster/production/var")
  }
}
