package ecdc.api

import akka.actor.ActorSystem
import com.amazonaws.auth.{ AWSCredentials, BasicAWSCredentials }
import com.amazonaws.regions.{ Region, Regions }
import com.amazonaws.services.ecs.AmazonECSAsyncClient
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient
import com.amazonaws.services.s3.AmazonS3EncryptionClient
import com.amazonaws.services.s3.model.{ CryptoConfiguration, KMSEncryptionMaterialsProvider }
import config.{ FileSystemTaskDefinitionResolver, TaskDefinitionResolver }
import ecdc.aws.ecs.EcsClient
import ecdc.aws.s3.S3EncryptedKeyProvider
import ecdc.crypto.{ CmsDecryptor, SecretKeyProvider }
import ecdc.git.GitActor
import scaldi.Module
import scaldi.akka.AkkaInjectable._

class ApplicationModule extends Module {

  implicit val system = inject[ActorSystem]

  bind[AmazonECSAsyncClient] to createEcsClient(
    inject[AWSCredentials]
  )

  bind[AmazonElasticLoadBalancingAsyncClient] to createElbClient(
    inject[AWSCredentials]
  )

  bind[EcsClient] to new EcsClient(
    inject[AmazonECSAsyncClient],
    inject[AmazonElasticLoadBalancingAsyncClient]
  )

  bind[ActorSystem] to ActorSystem("ECDC") destroyWith (_.shutdown())

  binding toProvider new GitActor(
    inject[String](identified by "git.repoUri"),
    inject[String](identified by "git.user"),
    inject[String](identified by "git.password")
  )

  bind[DeployController] to new DeployController(
    inject[EcsClient],
    inject[TaskDefinitionResolver],
    injectActorRef[GitActor]
  )

  bind[SecretKeyProvider] to new S3EncryptedKeyProvider(
    inject[String](identified by "s3.bucketName"),
    inject[String](identified by "s3.keyName"),
    inject[AmazonS3EncryptionClient]
  )

  bind[CmsDecryptor] to new CmsDecryptor(
    inject[SecretKeyProvider]
  )

  bind[TaskDefinitionResolver] to new FileSystemTaskDefinitionResolver(
    inject[CmsDecryptor]
  )

  bind[AWSCredentials] to new BasicAWSCredentials(
    inject[String](identified by "aws.accessKey"),
    inject[String](identified by "aws.secretKey")
  )

  bind[AmazonS3EncryptionClient] to createEncryptionClient(
    inject[String](identified by "kms.cmk.id"),
    inject[AWSCredentials]
  )

  private def createEcsClient(credentials: AWSCredentials): AmazonECSAsyncClient = {
    new AmazonECSAsyncClient(credentials)
      .withRegion(Region.getRegion(Regions.EU_WEST_1))
  }

  private def createElbClient(credentials: AWSCredentials): AmazonElasticLoadBalancingAsyncClient = {
    new AmazonElasticLoadBalancingAsyncClient(credentials)
      .withRegion(Region.getRegion(Regions.EU_WEST_1))
  }

  private def createEncryptionClient(cmkId: String, aWSCredentials: AWSCredentials): AmazonS3EncryptionClient = {
    val materialProvider = new KMSEncryptionMaterialsProvider(cmkId)
    new AmazonS3EncryptionClient(aWSCredentials, materialProvider,
      new CryptoConfiguration().withKmsRegion(Regions.EU_WEST_1))
      .withRegion(Region.getRegion(Regions.EU_WEST_1))
  }
}
