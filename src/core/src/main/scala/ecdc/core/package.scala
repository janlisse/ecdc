package ecdc

import com.typesafe.config.{ Config, ConfigValue }

import scala.collection.JavaConverters._
import scala.util.Try

package object core {
  implicit class EnhancedConfig(cfg: Config) {

    def getStringSeq(path: String): Seq[String] =
      if (cfg.hasPath(path)) {
        cfg.getStringList(path).asScala
      } else {
        Nil
      }

    def getConfigSeq(path: String): Seq[Config] =
      if (cfg.hasPath(path)) {
        cfg.getConfigList(path).asScala
      } else {
        Nil
      }

    def getConfigOptional(path: String): Option[Config] =
      if (cfg.hasPath(path)) {
        Some(cfg.getConfig(path))
      } else {
        None
      }

    def getBooleanOptional(path: String): Option[Boolean] = Try { cfg.getBoolean(path) }.toOption

    def getStringOptional(path: String): Option[String] = Try { cfg.getString(path) }.toOption

    def getIntOptional(path: String): Option[Int] = Try { cfg.getInt(path) }.toOption

    def getValueOptional(path: String): Option[ConfigValue] = Try { cfg.getValue(path) }.toOption

    def getOneOf(path: String, values: String*): Option[String] = values
      .find(v => Try {
        cfg.getString(path)
      }.toOption.contains(v))

    def toMap: Map[String, String] = {
      cfg.entrySet().asScala.map(e => e.getKey -> e.getValue.unwrapped().toString).toMap
    }
  }
}
