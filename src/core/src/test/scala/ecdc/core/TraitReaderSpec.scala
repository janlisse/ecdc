package ecdc.core

import java.io.File

import model.{ Cluster, Service }
import testutils.Spec

class TraitReaderSpec extends Spec {

  val baseDir = new File("./src/core/src/test/resources/testRepo")

  it should "parse traits properly" in {
    val traits = TraitReader(Service("foo"), baseDir).readTraits
    traits.size shouldBe 2
  }

  it should "ignore missing traits.conf" in {
    val traits = TraitReader(Service("bar"), baseDir).readTraits
    traits shouldBe Nil
  }

  it should "read trait with specific cluster name" in {
    val traits = TraitReader(Service("with-common-trait"), baseDir).readTraits
    traits should have size 1
    traits.head shouldBe ServiceTraitWithFixedCluster("logging-common", Cluster("common"))
  }

}
