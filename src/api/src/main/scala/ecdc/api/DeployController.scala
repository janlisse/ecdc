package ecdc.api

import java.util.Locale
import com.amazonaws.services.ecs.model.{ Service => _, _ }
import ecdc.core.TaskDefinitionResolver
import ecdc.aws.ecs.EcsClient
import ecdc.core.TaskDef
import ecdc.git.Git
import ecdc.git.Git.Timeout
import model.{ Cluster, Deployment, Service, Version }
import org.slf4j.LoggerFactory
import play.api.http.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.Try
import DeployController._
import scala.collection.JavaConversions._

class DeployController(ecsClient: EcsClient, configResolver: TaskDefinitionResolver, git: Git) extends Controller {

  val logger = LoggerFactory.getLogger(getClass)
  implicit val timeout = Timeout(5.seconds)

  def getTaskdef(cluster: Cluster, service: Service, version: Version) = Action.async {
    for {
      repoDir <- git.update()
      taskDef <- configResolver.resolve(repoDir, cluster, service, version)
    } yield {
      Ok(taskDef.toJson)
    }
  }

  def jsonOrForm[T](implicit jr: Reads[T], fr: FormReads[T]): BodyParser[Option[T]] = parse.using {
    request ⇒
      request.contentType.map(_.toLowerCase(Locale.ENGLISH)) match {
        case Some(MimeTypes.JSON) ⇒ BodyParsers.parse.json
          .map(jr.reads)
          .map(_.asOpt)
        case _ ⇒
          logger.debug("No Content-Type found. Using form parser.")
          BodyParsers.parse.tolerantFormUrlEncoded.map(fr.reads)
      }
  }

  def deployLatestService(c: Cluster, a: Service) = deployService(c, a, Version.latest)

  def deployService(c: Cluster, a: Service, v: Version) = Action.async(jsonOrForm[Int]) { req ⇒
    val deployRequest = Deployment(c, a, v, req.body)
    import deployRequest._
    for {
      repoDir <- git.update()
      taskDef <- configResolver.resolve(repoDir, cluster, service, version)
      taskDefArn ← ecsClient.registerTaskDef(taskDef)
      res ← ecsClient.updateService((taskDefArn, cluster, service, desiredCount.getOrElse(1)))
    } yield Ok(s"${res.getService}.\n")
  }
}

object DeployController {

  case class Arn(value: String) extends AnyVal

  implicit def tupleToUpdateRequest(t: (RegisterTaskDefinitionResult, Cluster, Service, Int)): UpdateServiceRequest =
    new UpdateServiceRequest()
      .withTaskDefinition(t._1.getTaskDefinition.getTaskDefinitionArn)
      .withCluster(t._2.name)
      .withService(t._3.name)
      .withDesiredCount(t._4)

  implicit def taskdefToRequest(taskDef: TaskDef): RegisterTaskDefinitionRequest = {
    implicit def toJava(o: Option[Int]): Integer = o match {
      case Some(x) => x
      case None => null
    }
    val res = new RegisterTaskDefinitionRequest()
    res.withContainerDefinitions(taskDef.containerDefinitions.map(cd =>
      new ContainerDefinition()
        .withCommand(cd.command)
        .withCpu(cd.cpu)
        //.withDisableNetworking(???)
        //.withDnsSearchDomains(???)
        //.withDnsServers(???)
        //.withDockerLabels(???)
        //.withDockerSecurityOptions(???)
        .withEntryPoint(cd.entryPoint)
        .withEnvironment(cd.environment.map(e =>
          new KeyValuePair()
            .withName(e.name)
            .withValue(e.value)
        ))
        .withEssential(cd.essential)
        //.withExtraHosts(???)
        //.withHostname(???)
        .withImage(cd.image.toString)
        //.withLinks(???)
        //.withLogConfiguration(???)
        .withMemory(cd.memory)
        //.withMountPoints(???)
        .withName(cd.name)
        .withPortMappings(cd.portMappings.map(p =>
          new PortMapping()
            .withContainerPort(p.containerPort)
            .withHostPort(p.hostPort)
            .withProtocol(p.protocol.toString)
        ))
        //.withPrivileged(???)
        //.withReadonlyRootFilesystem(???)
        //.withUlimits()
        //.withUser(???)
        .withVolumesFrom(cd.volumesFrom.map(v =>
          new VolumeFrom()
            .withReadOnly(v.readOnly)
            .withSourceContainer(v.sourceContainer)
        ))
    //.withWorkingDirectory(???)
    ))
    res.withFamily(taskDef.family)
    res.withVolumes(taskDef.volumes.map(vol =>
      new Volume()
        .withHost(vol.host.map(h =>
          new HostVolumeProperties()
            .withSourcePath(h.sourcePath.orNull)
        ).orNull)
        .withName(vol.name)
    ))
  }

  implicit val desiredCountJsonReads: Reads[Int] = (__ \ 'desiredCount).read[Int]

  implicit val desiredCountFormReads: FormReads[Int] = new FormReads[Int] {
    override def reads(form: Map[String, Seq[String]]): Option[Int] = form.get("desiredCount")
      .flatMap(_.headOption)
      .flatMap(s ⇒ Try { s.toInt }.toOption)
  }

  trait FormReads[T] {
    def reads(form: Map[String, Seq[String]]): Option[T]
  }
}
