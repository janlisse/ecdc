package crypto

import java.io.InputStreamReader
import java.security.{Security, PrivateKey}

import com.amazonaws.services.s3.AmazonS3EncryptionClient
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.{PKCSObjectIdentifiers, PrivateKeyInfo, RSAPrivateKey}
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}

trait SecretKeyProvider {
  val key: PrivateKey
}

class S3EncryptedKeyProvider(bucketName: String, keyName: String, encryptionClient: AmazonS3EncryptionClient) extends SecretKeyProvider {

  Security.addProvider(new BouncyCastleProvider())

  lazy override val key: PrivateKey = {
    val downloadedObject = encryptionClient.getObject(bucketName,
      keyName)
    val pemParser = new PEMParser(new InputStreamReader(downloadedObject.getObjectContent))
    val keyPair = pemParser.readObject().asInstanceOf[PEMKeyPair]
    val privateKeyInfoAsn1 = keyPair.getPrivateKeyInfo.parsePrivateKey()
    val converter = new JcaPEMKeyConverter().setProvider("BC")
    val privateKeyInfo = new PrivateKeyInfo(new
        AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE),
      RSAPrivateKey.getInstance(privateKeyInfoAsn1))
    converter.getPrivateKey(privateKeyInfo)
  }
}
