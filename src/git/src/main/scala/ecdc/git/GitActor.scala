package ecdc.git

import akka.actor.{ Actor, Props }
import java.io.File
import ecdc.git.Git._
import org.eclipse.jgit.api.{ Git => JGit }
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import scala.language.postfixOps

private[git] object GitActor {
  def props(repoUri: RepoUri, user: User, password: Password) = Props(classOf[GitActor], repoUri, user, password)
  case object Update
  case class UpdateDone(baseDir: File)
}

private[git] class GitActor(repoUri: RepoUri, user: User, password: Password) extends Actor {

  import GitActor._

  var git: Option[JGit] = None
  var baseDir: File = _
  val provider = new UsernamePasswordCredentialsProvider(user.value, password.value)

  override def receive = {
    case Update ⇒
      if (git.isDefined) pull() else clone(repoUri.value)
      sender() ! UpdateDone(baseDir)
  }

  private def pull() = git.map(_.pull()
    .setRebase(true)
    .setCredentialsProvider(provider)
    .call())

  private def clone(repoUri: String) = {
    val localPath = File.createTempFile("ecdc-config", "")
    localPath.delete()

    git = Some(JGit.cloneRepository()
      .setURI(repoUri)
      .setDirectory(localPath)
      .setCredentialsProvider(provider)
      .call())
    baseDir = localPath
  }
}
