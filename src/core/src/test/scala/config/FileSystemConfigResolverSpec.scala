package config

import java.io.File

import ecdc.crypto.CmsDecryptor
import model.Cluster
import org.scalatest.mock.MockitoSugar
import testutils.Spec

class FileSystemConfigResolverSpec extends Spec with MockitoSugar {

  val cmsDecryptor = mock[CmsDecryptor]

  it should "resolve env vars" in {
    val fsr = new FileSystemTaskDefinitionResolver(cmsDecryptor)
    val vars = fsr.resolveVariables(new File("./src/core/src/test/resources/testRepo"), "foo", Cluster("production"))
    vars should have size 2
  }

  it should "override variables according to hierarchy" in {
    val fsr = new FileSystemTaskDefinitionResolver(cmsDecryptor)
    val vars = fsr.resolveVariables(new File("./src/core/src/test/resources/testRepo"), "bar", Cluster("staging"))
    vars should have size 1
    val variable = vars.head
    variable.name shouldBe "SOME_VAR"
    variable.value shouldBe PlainValue("BAR2")
  }

  it should "resolve taskDef" in {
    val fsr = new FileSystemTaskDefinitionResolver(cmsDecryptor)
    val t = fsr.resolve(new File("./src/core/src/test/resources/testRepo"), "foo", Cluster("production"),
      Map("BUILD_NUMBER" -> "123"))
    t.isRight shouldBe true
    t.right.get should beJson("taskdef.json")
  }
}
