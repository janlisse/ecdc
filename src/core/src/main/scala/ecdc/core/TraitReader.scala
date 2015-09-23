package ecdc.core

import java.io.File
import scala.io.Source

object TraitReader {

  case class ServiceTrait(name: String)

  def readTraits(f: File): Seq[ServiceTrait] = {
    for (line <- Source.fromFile(f).getLines().toSeq)
      yield ServiceTrait(line)
  }
}
