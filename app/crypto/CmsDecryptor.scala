package crypto

import java.security.Security

import org.bouncycastle.cms.CMSEnvelopedDataParser
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64

import scala.collection.JavaConverters._

case class EncryptionType(v: String) extends AnyVal

class CmsDecryptor(keyProvider: SecretKeyProvider) {
  Security.addProvider(new BouncyCastleProvider())

  def decrypt(enc: (EncryptionType, String)): String = {
    val cedParser = new CMSEnvelopedDataParser(Base64.decode(enc._2))
    val recipientInfo = cedParser.getRecipientInfos.getRecipients.asScala.head
    val rec = new JceKeyTransEnvelopedRecipient(keyProvider.key)
    new String(recipientInfo.getContent(rec))
  }
}