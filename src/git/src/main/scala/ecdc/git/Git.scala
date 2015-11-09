package ecdc.git

import java.io.File
import akka.actor.{ ActorSystem, ActorRef }
import akka.pattern.ask
import akka.util
import ecdc.git.Git.Timeout
import ecdc.git.GitActor.{ Update, UpdateDone }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class Git private[git] (gitActor: ActorRef) {

  def update()(implicit timeout: Timeout, ec: ExecutionContext): Future[File] = {
    implicit val t = util.Timeout(timeout.value)
    (gitActor ? Update).mapTo[UpdateDone].map(_.baseDir)
  }
}

object Git {

  case class RepoUri(value: String)
  case class User(value: String)
  case class Password(value: String)
  case class Timeout(value: FiniteDuration)

  val system = ActorSystem("ecdc-git")

  def apply(repoUri: RepoUri, user: User, password: Password): Git =
    new Git(system.actorOf(GitActor.props(repoUri, user, password)))

  def terminate()(implicit ec: ExecutionContext): Future[Unit] = system.terminate().map(_ => Unit)
}
