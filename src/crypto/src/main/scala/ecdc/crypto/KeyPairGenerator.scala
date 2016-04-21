package ecdc.crypto

import java.io.StringWriter
import java.math.BigInteger
import java.security._
import java.util.Date

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509._
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.{ DefaultDigestAlgorithmIdentifierFinder, DefaultSignatureAlgorithmIdentifierFinder }

class KeyPairGenerator {
  Security.addProvider(new BouncyCastleProvider())

  val KEY_SIZE = 2048

  def create() = {
    val keyPair = generateRSAKeyPair()
    val priv = keyPair.getPrivate
    val pub = keyPair.getPublic

    println(convertToPEM(priv))
    val certificate = generateCertificate(keyPair, "/")
    println(convertToPEM(certificate))
  }

  private def generateRSAKeyPair() = {
    val generator = KeyPairGenerator.getInstance("RSA", "BC")
    generator.initialize(KEY_SIZE)
    generator.generateKeyPair()
  }

  private def generateCertificate(pair: KeyPair, dn: String) = {
    val builder = new X509v3CertificateBuilder(
      new X500Name("CN=" + dn),
      BigInteger.valueOf(new SecureRandom().nextLong()),
      new Date(System.currentTimeMillis() - 10000),
      new Date(System.currentTimeMillis() + 24L * 3600 * 50 * 365),
      new X500Name("CN=" + dn),
      SubjectPublicKeyInfo.getInstance(pair.getPublic.getEncoded))

    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
    builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature))
    builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth))
    builder.build(createContentSigner(pair))
  }

  private def createContentSigner(pair: KeyPair) = {
    val signatureAlgorithmId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA")
    val digestAlgorithmId = new DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithmId)
    val privateKey = PrivateKeyFactory.createKey(pair.getPrivate.getEncoded)
    new BcRSAContentSignerBuilder(signatureAlgorithmId, digestAlgorithmId).build(privateKey)
  }

  private def convertToPEM(content: Any): String = {
    val writer = new StringWriter()
    val pemWriter = new JcaPEMWriter(writer)
    pemWriter.writeObject(content)
    pemWriter.close()
    writer.toString
  }
}

