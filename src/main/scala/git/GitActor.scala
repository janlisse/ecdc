package git

import java.io.File

import akka.actor.{ Actor, Props }
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import scala.language.postfixOps

object GitActor {
  def props = Props[GitActor]
  case object Update
  case class UpdateDone(baseDir: File)
}

class GitActor(repoUri: String, user: String, password: String) extends Actor {

  import GitActor._

  var git: Option[Git] = None
  var baseDir: File = _
  val provider = new UsernamePasswordCredentialsProvider("user", "password")

  override def receive = {
    case Update ⇒
      if (git.isDefined) pull() else clone(repoUri)
      sender() ! UpdateDone(baseDir)
  }

  private def pull() = git.map(_.pull()
    .setRebase(true)
    .setCredentialsProvider(provider)
    .call())

  private def clone(repoUri: String) = {
    val localPath = File.createTempFile("ecdc-config", "")
    localPath.delete()

    git = Some(Git.cloneRepository()
      .setURI(repoUri)
      .setDirectory(localPath)
      .setCredentialsProvider(provider)
      .call())
    baseDir = localPath
  }
}
