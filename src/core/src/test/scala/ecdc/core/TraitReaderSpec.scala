package ecdc.core

import java.io.File
import testutils.Spec
import ecdc.core.TraitReader._

class TraitReaderSpec extends Spec {
  it should "read the traits from a file" in {
    val traits = readTraits(new File("./src/core/src/test/resources/traits.conf"))
    traits should have size 3
    traits.head shouldBe ServiceTrait("logging")
  }
}
