package ecdc.crypto

import java.io.{ InputStreamReader, InputStream }
import java.security.{ Security, PrivateKey }
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.{ RSAPrivateKey, PKCSObjectIdentifiers, PrivateKeyInfo }
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{ PEMKeyPair, PEMParser }

trait SecretKeyProvider {

  Security.addProvider(new BouncyCastleProvider())

  final lazy val key: PrivateKey = {
    val pemParser = new PEMParser(new InputStreamReader(keyAsStream))
    val keyPair = pemParser.readObject().asInstanceOf[PEMKeyPair]
    val privateKeyInfoAsn1 = keyPair.getPrivateKeyInfo.parsePrivateKey()
    val converter = new JcaPEMKeyConverter().setProvider("BC")
    val privateKeyInfo = new PrivateKeyInfo(new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE),
      RSAPrivateKey.getInstance(privateKeyInfoAsn1))
    converter.getPrivateKey(privateKeyInfo)
  }

  protected def keyAsStream: InputStream
}
