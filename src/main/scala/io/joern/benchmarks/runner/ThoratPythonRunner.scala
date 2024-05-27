package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import org.slf4j.LoggerFactory
import upickle.default.*

import java.net.{URI, URL}
import scala.util.Try

abstract class ThoratPythonRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with SingleFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  private val version        = "0.0.4"
  override val benchmarkName = s"Thorat Python v$version $creatorLabel"

  override protected val benchmarkUrl: URL = URI(
    s"https://github.com/DavidBakerEffendi/benchmark-for-taint-analysis-tools-for-python/archive/refs/tags/v$version.zip"
  ).toURL
  override protected val benchmarkFileName: String = s"benchmark-for-taint-analysis-tools-for-python-$version"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  override def initialize(): Try[File] = {
    if (!benchmarkBaseDir.exists) downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)
    else Try(benchmarkBaseDir)
  }

  protected lazy val testMetaData: Map[String, TAF] = {
    val testDir     = benchmarkBaseDir / "tests"
    val metaDataDir = benchmarkBaseDir / "tests_metadata"
    metaDataDir.list
      .filter(_.isDirectory)
      .flatMap { testDir =>
        val testName = testDir.name
        testDir.list
          .filter(f => f.`extension`.contains(".json") && f.name.endsWith("taf.json"))
          .map { testMetaDataFile =>
            val testMetaData = read[TAF](ujson.Readable.fromFile(testMetaDataFile.toJava))
            val targetFile   = testDir / testName / testMetaData.fileName
            targetFile.name -> testMetaData
          }
      }
      .toMap
  }

  protected def getExpectedTestOutcomes: Map[String, Boolean] = {
    testMetaData.flatMap { case (filename, metaData) =>
      metaData.findings.map(f => s"$filename:${f.sink.lineNo}" -> !f.isNegative)
    }
  }

  case class TAF(fileName: String, findings: Seq[TAFFinding]) derives ReadWriter

  case class TAFFinding(isNegative: Boolean, source: TAFStatement, sink: TAFStatement) derives ReadWriter

  case class TAFStatement(
    statement: String,
    methodName: String,
    className: String,
    lineNo: Int,
    targetName: String,
    targetNo: Int
  ) derives ReadWriter

}
