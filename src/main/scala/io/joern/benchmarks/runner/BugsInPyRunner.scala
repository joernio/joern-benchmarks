package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import upickle.default.*

import java.net.{URI, URL}
import scala.util.Try

abstract class BugsInPyRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with MultiFileDownloader {

  private val packageName = "bugs_in_py"

  override val benchmarkName = s"BugsInPy $creatorLabel"

  protected val packageNames: List[String] = List(
    "ansible",
    "cookiecutter",
    "PySnooper",
    "spacy",
    "sanic",
    "httpie",
    "keras",
    "matplotlib",
    "thefuck",
    "pandas",
    "black",
    "Scrapy",
    "luigi",
    "fastapi",
    "tornado",
    "tqdm",
    "youtube-dl"
  )

  override protected val benchmarkUrls: Map[String, URL] = Map(
    "bugs_in_py" -> URI(s"$baseDatasetsUrl/$benchmarksVersion/$packageName.zip").toURL
  )

  override protected val benchmarkDirName: String = "bugs_in_py"
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  protected def getExpectedTestOutcomes: Map[String, Boolean] = Map.empty

}
