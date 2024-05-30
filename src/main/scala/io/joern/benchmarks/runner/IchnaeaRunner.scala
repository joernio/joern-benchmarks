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

  override val benchmarkName = s"Ichnaea $creatorLabel"

  protected val packageNameAndVersion: Map[String, String] = Map("ichnaea" -> "0.2.0")

  override protected val benchmarkUrls: Map[String, URL] = packageNameAndVersion.map { case (packageName, version) =>
    packageName -> URI(s"$baseDatasetsUrl/v$version/$packageName.zip").toURL
  }

  override protected val benchmarkDirName: String = ""
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if a the sink is tainted.
    */
  protected def getExpectedTestOutcomes: Map[String, Boolean] = {
    // All packages in this dataset have a tainted sink `exec`/`eval`/`execSync`/`execFileSync`
    packageNameAndVersion.keys.map { packageName => packageName -> true }.toMap
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
