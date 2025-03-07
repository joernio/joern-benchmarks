package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import upickle.default.*

import java.net.{URI, URL}
import scala.util.Try

abstract class Defects4jRunner(datasetDir: File, creatorLabel: String, k: Int)
    extends BenchmarkRunner(datasetDir)
    with PerformanceTestRunner(k)
    with MultiFileDownloader {

  private val packageName = "defects4j"

  override val benchmarkName = s"Defects4j $creatorLabel"

  protected val packageNames: List[String] = List(
    "Chart",
    "Cli",
    "Closure",
    "Codec",
    "Collections",
    "Compress",
    "Csv",
    "Gson",
    "JacksonCore",
    "JacksonDatabind",
    "JacksonXml",
    "Jsoup",
    "JxPath",
    "Lang",
    "Math",
    "Mockito",
    "Time"
  )

  override protected val benchmarkUrls: Map[String, URL] = Map(
    "defects4j" -> URI(s"$baseDatasetsUrl/$benchmarksVersion/$packageName.zip").toURL
  )

  override protected val benchmarkDirName: String = "defects4j"
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

}
