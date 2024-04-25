object Versions {
  val cpg = parseVersion("cpgVersion")
  val joern = parseVersion("joernVersion")
  val overflowdb = parseVersion("overflowdbVersion")
  
  val betterFiles = "3.9.2"
  val log4j = "2.20.0"
  val scopt = "4.1.0"

  private def parseVersion(key: String): String = { 
    val versionRegexp = s""".*val $key[ ]+=[ ]?"(.*?)"""".r
    val versions: List[String] = scala.io.Source
      .fromFile("build.sbt")
      .getLines
      .filter(_.contains(s"val $key"))
      .collect { case versionRegexp(version) => version }
      .toList
    assert(
      versions.size == 1,
      s"""unable to extract $key from build.sbt, expected exactly one line like `val $key= "0.0.0-SNAPSHOT"`.""")
    versions.head
  }
}

