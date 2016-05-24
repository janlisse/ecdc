package ecdc.core

import java.io.File

import ecdc.crypto.TextDecryptor
import model.{ Cluster, Service, Version }
import org.scalatest.concurrent.ScalaFutures
import testutils.Spec
import scala.concurrent.ExecutionContext.Implicits.global

class FileSystemTaskDefinitionResolverSpec extends Spec {

  implicit val decryptor = new TextDecryptor {
    def decrypt(enc: String): String = {
      enc
    }
  }

  val baseDir = new File("./src/core/src/test/resources/testRepo")
  val service = Service("foo")
  val cluster = Cluster("production")
  val version = Version.latest

  it should "do a lot of things" in {
    val resolver = new FileSystemTaskDefinitionResolver

    ScalaFutures.whenReady(resolver.resolve(baseDir, cluster, Service("with-common-trait"), version)) { c =>
      c.taskDefinition.containerDefinitions should have size 1
      c.taskDefinition.containerDefinitions.head.image.toString shouldBe "repo/image/with-common-trait:latest"
    }
  }
}
