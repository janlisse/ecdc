package ecdc.core

import java.io.File

object TraitReader {

  case class ServiceTrait(name: String)

  def readTraits(f: File): Seq[ServiceTrait] = ???
}
