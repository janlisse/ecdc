package ecdc.aws.ecs

import com.amazonaws.services.ecs.AmazonECSAsyncClient
import com.amazonaws.services.ecs.model._
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient
import com.amazonaws.services.elasticloadbalancing.model._

import scala.concurrent.Future

class EcsClient(amazonECSAsyncClient: AmazonECSAsyncClient,
    elbClient: AmazonElasticLoadBalancingAsyncClient) {

  def describeService(describeServiceRequest: DescribeServicesRequest): Future[DescribeServicesResult] = {
    wrapAsyncMethod(amazonECSAsyncClient.describeServicesAsync, describeServiceRequest)
  }

  def listClusters(): Future[ListClustersResult] = {
    val listClusters = new ListClustersRequest()
    wrapAsyncMethod(amazonECSAsyncClient.listClustersAsync, listClusters)
  }

  def registerTaskDef(registerTaskDefinitionRequest: RegisterTaskDefinitionRequest): Future[RegisterTaskDefinitionResult] = {
    wrapAsyncMethod(amazonECSAsyncClient.registerTaskDefinitionAsync, registerTaskDefinitionRequest)
  }

  def updateService(updateServiceRequest: UpdateServiceRequest): Future[UpdateServiceResult] = {
    wrapAsyncMethod(amazonECSAsyncClient.updateServiceAsync, updateServiceRequest)
  }

  def createService(createServiceRequest: CreateServiceRequest): Future[CreateServiceResult] = {
    wrapAsyncMethod(amazonECSAsyncClient.createServiceAsync, createServiceRequest)
  }

  def configureLbHealthCheck(configureHealthCheckRequest: ConfigureHealthCheckRequest): Future[ConfigureHealthCheckResult] =
    wrapAsyncMethod(elbClient.configureHealthCheckAsync, configureHealthCheckRequest)

  def applyLbSecurityGroups(applySecurityGroupsToLoadBalancerRequest: ApplySecurityGroupsToLoadBalancerRequest): Future[ApplySecurityGroupsToLoadBalancerResult] =
    wrapAsyncMethod(elbClient.applySecurityGroupsToLoadBalancerAsync,
      applySecurityGroupsToLoadBalancerRequest)

  def createElb(createElbRequest: CreateLoadBalancerRequest): Future[CreateLoadBalancerResult] =
    wrapAsyncMethod(elbClient.createLoadBalancerAsync, createElbRequest)
}
