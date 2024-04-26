package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.JavaSrcCpgCreator
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import org.slf4j.LoggerFactory

import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

class OWASPJavaRunner(datasetDir: File)
    extends BenchmarkRunner(datasetDir)
    with ArchiveDownloader
    with JavaSrcCpgCreator {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = "OWASP Java v1.2"

  override protected val benchmarkUrl: URL = URI(
    "https://github.com/OWASP-Benchmark/BenchmarkJava/archive/refs/tags/1.2beta.zip"
  ).toURL
  override protected val benchmarkFileName: String = "BenchmarkJava-1.2beta"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  override def initialize(): Try[File] = downloadBenchmark

  override def findings(testName: String)(implicit cpg: Cpg): List[Finding] = {
    cpg.findings.filter(_.keyValuePairs.exists(_.value.split(':').headOption.contains(testName))).l
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runOWASP()
    }
  }

  private def getExpectedTestOutcomes: Map[String, Boolean] = {
    val expectedResultsDir = benchmarkBaseDir / "src" / "main" / "java" / "org" / "owasp" / "benchmark" / "testcode"
    expectedResultsDir.list
      .filter(_.`extension`.contains(".xml"))
      .map { testMetaDataFile =>
        val testMetaData = XML.loadFile(testMetaDataFile.toJava)
        val testName     = testMetaDataFile.nameWithoutExtension
        testName -> (testMetaData \ "vulnerability").text.toBoolean
      }
      .toMap
  }

  private def runOWASP(): Result = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    createCpg(benchmarkBaseDir) match {
      case Failure(exception) =>
        logger.error("Unable to generate CPG for OWASP benchmark")
        Result()
      case Success(cpg) =>
        implicit val cpgImpl: Cpg = cpg
        val testResults = expectedTestOutcomes
          .map { case (testName, outcome) =>
            TestEntry(testName, compare(testName, outcome))
          }
          .sortBy(_.testName)
          .l
        Result(testResults)
    }
  }

}
