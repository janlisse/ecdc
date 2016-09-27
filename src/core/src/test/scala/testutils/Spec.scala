package testutils

import ecdc.core.TaskDef
import ecdc.core.TaskDef.ContainerDefinition
import org.scalatest._

import scala.io.Source

trait Spec extends FlatSpec with ShouldMatchers with JsValueMatchers {
  def readFile(path: String): Source = Source.fromURL(getClass.getResource(path))
}

object ServiceConfigUtils {
  implicit class TaskDefUtils(td: TaskDef) {
    def containerByName(n: String): ContainerDefinition = {
      td.containerDefinitions.find(cd => cd.name == n) match {
        case None => throw new RuntimeException(s"Cant find container $n in definitions")
        case Some(cd) => cd
      }
    }
  }
}
