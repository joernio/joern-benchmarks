package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import upickle.default.*

import java.net.{URI, URL}
import scala.util.{Try, Using}

abstract class IchnaeaRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with MultiFileDownloader {

  private val version     = "0.2.0"
  private val packageName = "ichnaea"

  override val benchmarkName = s"Ichnaea $creatorLabel"

  protected val packageNames: List[String] = List(
    "chook-growl-reporter",
    "cocos-utils",
    "gm",
    "fish",
    "git2json",
    "growl",
    "libnotify",
    "m-log",
    "mixin-pro",
    "modulify",
    "mongo-parse",
    "mongoosemask",
    "mongoosify",
    "node-os-utils",
    "node-wos",
    "office-converter",
    "os-uptime",
    "osenv",
    "pidusage",
    "pomelo-monitor",
    "system-locale",
    "systeminformation"
  )

  protected val sinkNames: Set[String] = Set("exec", "eval", "execSync", "execFileSync")

  override protected val benchmarkUrls: Map[String, URL] = Map(
    "ichnaea" -> URI(s"$baseDatasetsUrl/v$version/$packageName.zip").toURL
  )

  override protected val benchmarkDirName: String = "ichnaea"
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if a the sink is tainted.
    */
  protected def getExpectedTestOutcomes: Map[String, Boolean] = {
    // Most packages in this dataset have a tainted sink `exec`/`eval`/`execSync`/`execFileSync`
    val nonVulnerableApps = Set("node-wos", "os-uptime", "osenv", "system-locale", "systeminformation")
    packageNames.map { packageName => packageName -> !nonVulnerableApps.contains(packageName) }.toMap
  }

  implicit val urlRw: ReadWriter[URL] = readwriter[ujson.Value]
    .bimap[URL](
      x => ujson.Str(x.toString),
      {
        case json @ (j: ujson.Str) => URI(json.str).toURL
        case x                     => throw RuntimeException(s"Unexpected value type for URL strings: ${x.getClass}")
      }
    )

  case class NPMRegistryResponse(dist: NPMDistBody) derives ReadWriter

  case class NPMDistBody(tarball: URL) derives ReadWriter

}
