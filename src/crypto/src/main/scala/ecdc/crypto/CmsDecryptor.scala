package ecdc.crypto

import java.security.Security
import org.bouncycastle.cms.CMSEnvelopedDataParser
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import scala.collection.JavaConverters._

trait TextDecryptor {
  def decrypt(enc: String): String
}

class CmsDecryptor(keyProvider: SecretKeyProvider) extends TextDecryptor {
  Security.addProvider(new BouncyCastleProvider())

  def decrypt(enc: String): String = {
    val cedParser = new CMSEnvelopedDataParser(Base64.decode(enc))
    val recipientInfo = cedParser.getRecipientInfos.getRecipients.asScala.head
    val rec = new JceKeyTransEnvelopedRecipient(keyProvider.key)
    new String(recipientInfo.getContent(rec))
  }
}
