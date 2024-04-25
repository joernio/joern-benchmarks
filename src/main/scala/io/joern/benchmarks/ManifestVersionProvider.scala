package io.joern.benchmarks

import java.io.IOException
import java.util.jar.{Attributes, Manifest}
import scala.collection.mutable

/** Returns version information from the CPGFL JAR file's <code>/META-INF/MANIFEST.MF</code> file.
  */
class ManifestVersionProvider {
  def getVersion: String = {
    val resources = classOf[ManifestVersionProvider].getClassLoader.getResources("META-INF/MANIFEST.MF")
    while ({
      resources.hasMoreElements
    }) {
      val url = resources.nextElement
      try {
        val manifest = new Manifest(url.openStream)
        if (isApplicableManifest(manifest)) {
          val attr        = manifest.getMainAttributes
          val versionInfo = new mutable.StringBuilder()
          this.getVersion(attr) match {
            case Some(version) => versionInfo.append("\"").append(version).append("\" ")
            case None          => versionInfo.append("0.0.0 ")
          }
          this.getBuildDate(attr) match {
            case Some(date) => versionInfo.append(date)
            case None       =>
          }
          this.getBuildNumber(attr) match {
            case Some(buildNo) => versionInfo.append(" (build ").append(buildNo).append(")")
            case None          =>
          }
          return versionInfo.toString()
        }
      } catch {
        case _: IOException => return "<unknown>"
      }
    }
    "<unknown>"
  }

  private def isApplicableManifest(manifest: Manifest): Boolean = {
    val attributes = manifest.getMainAttributes
    "cpgfl" == this.getTitle(attributes).getOrElse("")
  }

  private def get(attributes: Attributes, key: String): Option[String] =
    Option(attributes.get(new Attributes.Name(key))).map(a => a.toString)

  private def getTitle(attributes: Attributes): Option[String] = get(attributes, "Implementation-Title")

  private def getVersion(attributes: Attributes): Option[String] =
    get(attributes, "Implementation-Version").map(f => f.split("\\+")(0))

  private def getBuildNumber(attributes: Attributes): Option[String] =
    get(attributes, "Implementation-Version").flatMap { f =>
      val versionInfo = f.split("\\+")
      if (versionInfo.length < 2) None
      else Option(f.split("\\+")(1).replaceAll("\\d+-", ""))
    }

  private def getBuildDate(attributes: Attributes): Option[String] = {
    get(attributes, "Implementation-Version").flatMap { f =>
      val versionInfo = f.split("\\+")
      if (versionInfo.length < 3) None
      else Option(f.split("\\+")(2).replaceAll("-\\d+", ""))
    } match {
      case Some(rawDate) if rawDate.length >= 8 =>
        Option(Seq(rawDate.substring(0, 4), rawDate.substring(4, 6), rawDate.substring(6, 8)).mkString("-"))
      case _ => None
    }
  }

}
