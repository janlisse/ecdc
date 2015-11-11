package ecdc.core

import java.io.File
import scala.io.Source

object TraitReader {

  case class ServiceTrait(name: String)

  def readTraits(file: File): Seq[ServiceTrait] = {
    val lines = Source.fromFile(file).getLines()
    for (line <- lines.toArray) yield ServiceTrait(line)
  }
}
