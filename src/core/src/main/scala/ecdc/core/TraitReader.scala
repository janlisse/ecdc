package ecdc.core

import java.io.File
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

object TraitReader {

  case class ServiceTrait(name: String)

  def readTraits(file: File): Seq[ServiceTrait] = ConfigFactory
    .parseFile(file)
    .getStringList("traits")
    .asScala
    .map(ServiceTrait)
}
