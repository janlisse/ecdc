package ecdc.core

import java.io.File

import model.Service
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

}
