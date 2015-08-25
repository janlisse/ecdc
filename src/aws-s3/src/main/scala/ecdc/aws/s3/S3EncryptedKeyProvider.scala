package ecdc.aws.s3

import java.io.InputStream

import com.amazonaws.services.s3.AmazonS3EncryptionClient
import ecdc.crypto.SecretKeyProvider

class S3EncryptedKeyProvider(bucketName: String, keyName: String, encryptionClient: AmazonS3EncryptionClient) extends SecretKeyProvider {
  override protected def keyAsStream: InputStream = encryptionClient.getObject(bucketName, keyName).getObjectContent
}
