package object model {

  case class Service(name: String) extends AnyVal

  case class Cluster(name: String) extends AnyVal

  case class Version(value: String) extends AnyVal

  object Version {
    val latest: Version = Version("latest")
  }
}
