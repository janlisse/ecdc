package ecdc.api

import java.io.File
import java.util.Locale
import com.amazonaws.services.ecs.model.{ Service => _, _ }
import config.TaskDefinitionResolver
import ecdc.api.DeployController._
import ecdc.aws.ecs.EcsClient
import ecdc.git.Git
import ecdc.git.Git.Timeout
import model.{ Cluster, Deployment, Service, Version }
import org.slf4j.LoggerFactory
import play.api.http.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class DeployController(ecsClient: EcsClient, configResolver: TaskDefinitionResolver, git: Git) extends Controller {

  val logger = LoggerFactory.getLogger(getClass)

  def getTaskdef(cluster: Cluster, application: Service, version: Version) = Action.async {
    resolveTaskDef(cluster, application, version).map(taskDef ⇒ Ok(Json toJson taskDef))
  }

  def jsonOrForm[T](implicit jr: Reads[T], fr: FormReads[T]): BodyParser[Option[T]] = parse.using {
    request ⇒
      request.contentType.map(_.toLowerCase(Locale.ENGLISH)) match {
        case Some(MimeTypes.JSON) ⇒ BodyParsers.parse.json
          .map(value ⇒ {
            println(value)
            jr.reads(value)
          })
          .map(_.asOpt)
        case _ ⇒
          logger.debug("No Content-Type found. Using form parser.")
          BodyParsers.parse.tolerantFormUrlEncoded.map(fr.reads)
      }
  }

  def deployLatestService(c: Cluster, a: Service) = deployService(c, a, Version.latest)

  def deployService(c: Cluster, a: Service, v: Version) = Action.async(jsonOrForm[Int](desiredCountJsonReads, desiredCountFormReads)) { req ⇒
    val deployRequest = Deployment(c, a, v, req.body)
    import deployRequest._
    for {
      taskDefArn ← registerTaskDef(service, cluster, version)
      res ← updateService(cluster, service, taskDefArn, desiredCount.getOrElse(1))
    } yield Ok(s"${res.getService}.\n")
  }

  private def updateService(cluster: Cluster, service: Service, taskDefArn: String, desiredCount: Int): Future[UpdateServiceResult] = {
    val usr = new UpdateServiceRequest()
      .withCluster(cluster.name)
      .withService(service.name)
      .withTaskDefinition(taskDefArn)
      .withDesiredCount(desiredCount)
    ecsClient.updateService(usr)
  }

  private def registerTaskDef(application: Service, environment: Cluster, version: Version): Future[String] = {
    import ecdc.aws.ecs.RegisterTaskDefinitionReads._
    for {
      taskDef ← resolveTaskDef(environment, application, version)
      taskDefRequest = taskDef.as[RegisterTaskDefinitionRequest]
      regResult ← ecsClient.registerTaskDef(taskDefRequest)
    } yield regResult.getTaskDefinition.getTaskDefinitionArn
  }

  private def resolveTaskDef(cluster: Cluster, application: Service, version: Version): Future[JsValue] = {
    implicit val timeout = Timeout(5.seconds)
    for {
      repoDir ← git.update()
      taskDef = getTaskDefJson(repoDir, application, cluster,
        Map(
          "BUILD_NUMBER" -> version.value,
          "SERVICE" -> application.name,
          "CLUSTER" -> cluster.name))
    } yield taskDef
  }

  private def getTaskDefJson(baseDir: File, app: Service, env: Cluster,
    additionalVars: Map[String, String]): JsValue = {
    logger.info(s"Resolve taskdef from repoDir: $baseDir")
    configResolver.resolve(baseDir, app.name, env, additionalVars).fold(
      err ⇒ throw new IllegalArgumentException(s"Error resolving taskdef for app ${app.name} and cluster ${env.name}: $err"),
      x ⇒ x
    )
  }
}

object DeployController {

  val desiredCountJsonReads: Reads[Int] = (__ \ 'desiredCount).read[Int]
  val desiredCountFormReads: FormReads[Int] = new FormReads[Int] {
    override def reads(form: Map[String, Seq[String]]): Option[Int] = form.get("desiredCount")
      .flatMap(_.headOption)
      .flatMap(s ⇒ Try { s.toInt }.toOption)
  }

  trait FormReads[T] {
    def reads(form: Map[String, Seq[String]]): Option[T]
  }
}
