package ecdc.aws.ecs

import com.amazonaws.services.ecs.model._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.collection.JavaConverters._

object RegisterTaskDefinitionReads {

  implicit val kvReads: Reads[KeyValuePair] = (
    (__ \ "name").read[String] and
    (__ \ 'value).read[String]
  )((name, value) ⇒ {
      val kv = new KeyValuePair()
      kv.setName(name)
      kv.setValue(value)
      kv
    })

  implicit val pmReads: Reads[PortMapping] = (
    (__ \ "containerPort").read[Int] and
    (__ \ 'hostPort).read[Int]
  )((cp, hp) ⇒ {
      val pm = new PortMapping()
      pm.setContainerPort(cp)
      pm.setHostPort(hp)
      pm
    })

  implicit val cdReads: Reads[ContainerDefinition] = (
    (__ \ "name").read[String] and
    (__ \ 'image).read[String] and
    (__ \ 'cpu).read[Int] and
    (__ \ 'memory).read[Int] and
    (__ \ 'memoryReservation).read[Int] and
    (__ \ 'essential).read[Boolean] and
    (__ \ 'command).read[Seq[String]] and
    (__ \ 'environment).read[Seq[KeyValuePair]] and
    (__ \ 'portMappings).readNullable[Seq[PortMapping]]
  )((name: String, image: String, cpu: Int, memory: Int, memoryReservation: Int,
      essential: Boolean, command: Seq[String], environment: Seq[KeyValuePair],
      portMappings: Option[Seq[PortMapping]]) ⇒ {
      val cd = new ContainerDefinition()
      cd.setName(name)
      cd.setImage(image)
      cd.setCpu(cpu)
      cd.setMemory(memory)
      cd.setMemoryReservation(memory)
      cd.setEssential(essential)
      cd.setCommand(command.asJava)
      cd.setEnvironment(environment.asJava)
      portMappings.foreach(pm ⇒ cd.setPortMappings(pm.asJava))
      cd
    })

  implicit val rtdrReads: Reads[RegisterTaskDefinitionRequest] = (
    (__ \ "family").read[String] and
    (__ \ 'containerDefinitions).read[Seq[ContainerDefinition]]
  )((family, cds) ⇒ {
      val rtdr = new RegisterTaskDefinitionRequest()
      rtdr.setFamily(family)
      rtdr.setContainerDefinitions(cds.asJava)
      rtdr
    })
}
