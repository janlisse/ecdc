package ecdc.core

import config.Arm
import java.io.File
import scala.io.Source

object TraitReader extends Arm {

  case class ServiceTrait(name: String)

  def readTraits(file: File): Seq[ServiceTrait] = {
    using(Source.fromFile(file)) {
      s ⇒
        {
          for (line <- s.getLines().toArray)
            yield ServiceTrait(line)
        }
    }
  }
}
