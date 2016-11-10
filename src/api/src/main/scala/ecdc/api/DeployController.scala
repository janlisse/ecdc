package ecdc.api

import com.amazonaws.services.ecs.model.{ Service => _, _ }
import com.amazonaws.services.elasticloadbalancing.model.{ HealthCheck => AwsHealthCheck }
import com.amazonaws.services.elasticloadbalancing.model.{ ConfigureHealthCheckRequest, Listener, CreateLoadBalancerRequest }
import ecdc.api.DeployController._
import ecdc.aws.ecs.EcsClient
import ecdc.core.{ HealthCheck, ServiceConfig, TaskDef, TaskDefinitionResolver }
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
  implicit val timeout = Timeout(30.seconds)

  def getLatestTaskdef(c: Cluster, s: Service) = getTaskdef(c, s, Version.latest)

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
        updateService(service, cluster, desiredCount, dsr.desiredCount)
      } else {
        logger.info("Service doesn't exist yet, create it now.")
        createServiceStack(service, cluster, serviceConfig, desiredCount, dsr.desiredCount)
      }
    }

    def updateService(service: Service, cluster: Cluster, desiredCount: Option[Int], lastDesiredCount: Int): Future[Unit] = {
      val usr = new UpdateServiceRequest()
        .withCluster(cluster.name)
        .withService(service.name)
        .withTaskDefinition(taskDefArn.value)
        .withDesiredCount(getDesiredCount(desiredCount, lastDesiredCount))
      ecsClient.updateService(usr).map(_ => ()) //TODO figure out how to test if deployment went fine
    }

    def createServiceStack(service: Service, cluster: Cluster, serviceConfig: ServiceConfig,
      desiredCount: Option[Int], lastDesiredCount: Int): Future[Unit] = {

      val loadBalancerName = serviceConfig.loadBalancer
        .flatMap(lb => lb.name)
        .getOrElse(s"${service.name}-${cluster.name}")
      for {
        lbResult <- createLoadBalancer(loadBalancerName, service, serviceConfig.loadBalancer)
        lbHealthCheckResult <- configureLbHealthCheck(loadBalancerName, service, serviceConfig.loadBalancer.map(_.healthCheck))
        serviceResult <- createService(loadBalancerName, serviceConfig, service, cluster, desiredCount, lastDesiredCount)
      } yield ()
    }

    def configureLbHealthCheck(loadBalancerName: String, service: Service, hcOpt: Option[HealthCheck]): Future[Unit] =
      hcOpt.map { hc =>
        val healthCheck = new AwsHealthCheck()
          .withTarget(hc.target)
          .withHealthyThreshold(hc.healthyThreshold)
          .withUnhealthyThreshold(hc.unhealthyThreshold)
          .withInterval(hc.interval)
          .withTimeout(hc.timeout)
        val hcr = new ConfigureHealthCheckRequest()
          .withLoadBalancerName(loadBalancerName)
          .withHealthCheck(healthCheck)
        ecsClient.configureLbHealthCheck(hcr).map(_ => ())
      }.getOrElse(Future.successful(()))

    def createLoadBalancer(loadBalancerName: String, service: Service, lbOpt: Option[ecdc.core.LoadBalancer]): Future[Unit] = {
      lbOpt.map { lb =>
        val clbr = new CreateLoadBalancerRequest()
          .withListeners(Seq(new Listener()
            .withInstancePort(lb.instancePort)
            .withInstanceProtocol(lb.instanceProtocol)
            .withLoadBalancerPort(lb.loadBalancerPort)
            .withProtocol(lb.protocol)
          //.withSSLCertificateId() TODO add SSL support
          ))
          .withLoadBalancerName(loadBalancerName)
          .withScheme(lb.scheme)
          .withSecurityGroups(lb.securityGroups)
          .withSubnets(lb.subnets)
        ecsClient.createElb(clbr).map(_ => ())
      }.getOrElse(Future.successful(()))
    }

    def createService(loadBalancerName: String, serviceConfig: ServiceConfig, service: Service, cluster: Cluster, desiredCount: Option[Int], lastDesiredCount: Int) = {
      val csr = new CreateServiceRequest()
        .withCluster(cluster.name)
        .withServiceName(service.name)
        .withTaskDefinition(taskDefArn.value)
        .withDesiredCount(getDesiredCount(desiredCount, lastDesiredCount))
      val loadbalancedContainer = serviceConfig.taskDefinition.getLoadbalancedServiceContainer match {
        case None => serviceConfig.taskDefinition.containerDefinitions.head
        case Some(c) => c
      }
      val withLb = serviceConfig.loadBalancer.fold(csr)(lb =>
        csr.withLoadBalancers(
          new LoadBalancer()
            .withLoadBalancerName(loadBalancerName)
            .withContainerName(loadbalancedContainer.name)
            .withContainerPort(loadbalancedContainer.portMappings.head.containerPort)
        ).withRole(lb.serviceRole))

      logger.info(s"Creating loadbalancer with name $loadBalancerName pointing to ${loadbalancedContainer.name}")

      ecsClient.createService(withLb).map(_ => ()) //TODO figure out how to test if deployment went fine
    }

    def getDesiredCount(desiredCount: Option[Int], runningCount: Int) = (desiredCount, runningCount) match {
      case (Some(desired), runnning) => desired
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
        .withLinks(cd.links)
        .withLogConfiguration(cd.logConfiguration.map(l =>
          new LogConfiguration()
            .withLogDriver(l.logDriver)
            .withOptions(l.options)
        ).orNull)
        .withMemory(cd.memory)
        .withMountPoints(cd.mountPoints.map(mp =>
          new MountPoint().withContainerPath(mp.containerPath)
            .withSourceVolume(mp.sourceVolume)
            .withReadOnly(mp.readOnly)))
        .withUlimits(cd.ulimits.map(ul =>
          new Ulimit().withName(ul.name)
            .withSoftLimit(ul.softLimit)
            .withHardLimit(ul.hardLimit))
        )
        .withVolumesFrom(cd.volumesFrom.map(vf =>
          new VolumeFrom()
            .withSourceContainer(vf.sourceContainer)
            .withReadOnly(vf.readOnly))
        )
        .withName(cd.name)
        .withPortMappings(cd.portMappings.map(p =>
          new PortMapping()
            .withContainerPort(p.containerPort)
            .withHostPort(p.hostPort)
            .withProtocol(p.protocol.toString)
        ))
    //.withPrivileged(???)
    //.withReadonlyRootFilesystem(???)
    //.withUser(???)
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
    res.withTaskRoleArn(taskDef.taskRoleArn.orNull)
  }
}
