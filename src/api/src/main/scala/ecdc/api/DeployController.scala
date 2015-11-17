package ecdc.api

import com.amazonaws.services.ecs.model.{ Service => _, _ }
import ecdc.api.DeployController._
import ecdc.aws.ecs.EcsClient
import ecdc.core.{ ServiceConfig, TaskDef, TaskDefinitionResolver }
import ecdc.git.Git
import ecdc.git.Git.Timeout
import model.{ Cluster, Service, Version }
import org.slf4j.LoggerFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

class DeployController(ecsClient: EcsClient, configResolver: TaskDefinitionResolver, git: Git) extends Controller {

  val logger = LoggerFactory.getLogger(getClass)
  implicit val timeout = Timeout(5.seconds)

  def getTaskdef(cluster: Cluster, service: Service, version: Version) = Action.async {
    for {
      repoDir <- git.update()
      serviceConfig <- configResolver.resolve(repoDir, cluster, service, version)
    } yield Ok(serviceConfig.taskDefinition.toJson)
  }

  def deployLatestService(c: Cluster, a: Service) = deployService(c, a, Version.latest)

  def deployService(cluster: Cluster, service: Service, version: Version) = Action.async { req ⇒
    for {
      repoDir <- git.update()
      serviceConfig <- configResolver.resolve(repoDir, cluster, service, version)
      taskDefResult ← ecsClient.registerTaskDef(serviceConfig.taskDefinition)
      taskDefArn = Arn(taskDefResult.getTaskDefinition.getTaskDefinitionArn)
      res ← createOrUpdateService(serviceConfig, taskDefArn, cluster, service, version)
    } yield Ok(s"$service.\n")
  }

  def createOrUpdateService(serviceConfig: ServiceConfig,
    taskDefArn: Arn,
    cluster: Cluster,
    service: Service,
    version: Version): Future[Unit] = {

    def createOrUpdateInner(dsr: DescribeServiceResult, taskDefArn: Arn): Future[Unit] = {
      val desiredCount = serviceConfig.desiredCount
      if (dsr.exists) {
        val usr = new UpdateServiceRequest()
          .withCluster(dsr.cluster.name)
          .withService(service.name)
          .withTaskDefinition(taskDefArn.value)
          .withDesiredCount(getDesiredCount(desiredCount, dsr.desiredCount))
        ecsClient.updateService(usr).map(_ => ()) //TODO figure out how to test if deployment went fine
      } else {
        logger.info("Service doesn't exist yet, create it now.")
        val csr = new CreateServiceRequest()
          .withCluster(cluster.name)
          .withServiceName(service.name)
          .withTaskDefinition(taskDefArn.value)
          .withDesiredCount(getDesiredCount(desiredCount, dsr.desiredCount))

        val withLb = serviceConfig.loadBalancer.fold(csr)(lb => csr.withLoadBalancers(lb))

        //.withRole() TODO add role
        ecsClient.createService(withLb).map(_ => ()) //TODO figure out how to test if deployment went fine
      }
    }

    def getDesiredCount(desiredCount: Option[Int], runningCount: Int) = (desiredCount, runningCount) match {
      case (Some(desired), runnning) => if (runnning > desired) runnning else desired
      case (None, running) => if (running == 0) 1 else running
    }

    for {
      dsr ← ecsClient.describeService(describeServiceRequest(service, cluster))
      result = describeServiceResult(service, cluster, dsr)
      res ← createOrUpdateInner(result, taskDefArn)
    } yield ()
  }
}

object DeployController {

  case class Arn(value: String) extends AnyVal
  case class DescribeServiceResult(service: Service, cluster: Cluster, exists: Boolean, desiredCount: Int)

  def describeServiceResult(service: Service, cluster: Cluster, dsr: DescribeServicesResult): DescribeServiceResult =
    dsr.getServices.headOption.map(s => DescribeServiceResult(service, cluster, exists = true, s.getDesiredCount))
      .getOrElse(DescribeServiceResult(service, cluster, exists = false, 0))

  def describeServiceRequest(service: Service, cluster: Cluster) =
    new DescribeServicesRequest().withCluster(cluster.name).withServices(service.name)

  implicit def loadBalancerToAwsLb(lb: ecdc.core.LoadBalancer): LoadBalancer = ??? //TODO implement

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
}
