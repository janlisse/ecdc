package crypto

import java.io.{ ByteArrayInputStream, FileInputStream }

import com.amazonaws.services.s3.AmazonS3EncryptionClient
import com.amazonaws.services.s3.model.{ ObjectMetadata, PutObjectRequest }
import org.apache.commons.io.IOUtils

class SecretKeyUpload(bucketName: String, keyName: String, encryptionClient: AmazonS3EncryptionClient) {

  def upload(): Unit = {
    val rawKey = IOUtils.toByteArray(new FileInputStream("private_key.pkcs7.pem"))
    encryptionClient.putObject(new PutObjectRequest(bucketName, keyName,
      new ByteArrayInputStream(rawKey), new ObjectMetadata()))
  }
}
