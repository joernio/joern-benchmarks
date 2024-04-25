package io.joern.benchmarks.runner

import better.files.File
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import org.slf4j.LoggerFactory
import io.shiftleft.codepropertygraph.generated.Cpg
import io.joern.benchmarks.cpggen.JavaSrcCpgCreator
import io.shiftleft.semanticcpg.language.*
import io.joern.benchmarks.passes.{JavaTaggingPass, FindingsPass}
import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI}
import scala.util.{Failure, Success, Try, Using}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import io.joern.benchmarks.*

import scala.xml.XML

class OWASPJavaV1_2Runner(datasetDir: File) extends BenchmarkRunner(datasetDir) with JavaSrcCpgCreator {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = "OWASP Java v1.2"

  protected val benchmarkUrl = URI(
    "https://github.com/OWASP-Benchmark/BenchmarkJava/archive/refs/tags/1.2beta.zip"
  ).toURL

  private val benchmarkFileName = "BenchmarkJava-1.2beta"
  private val benchmarkBaseDir  = datasetDir / benchmarkFileName

  override def initialize(): Try[File] = Try {
    if (!benchmarkBaseDir.exists || benchmarkBaseDir.list.forall(_.isDirectory)) {
      benchmarkBaseDir.createDirectoryIfNotExists(createParents = true)
      var connection: Option[HttpURLConnection] = None
      val targetFile                            = datasetDir / s"$benchmarkFileName.zip"
      try {
        connection = Option(benchmarkUrl.openConnection().asInstanceOf[HttpURLConnection])
        connection.foreach {
          case conn if conn.getResponseCode == HttpURLConnection.HTTP_OK =>
            Using.resources(conn.getInputStream, new FileOutputStream(targetFile.pathAsString)) { (is, fos) =>
              val buffer = new Array[Byte](4096)
              Iterator
                .continually(is.read(buffer))
                .takeWhile(_ != -1)
                .foreach(bytesRead => fos.write(buffer, 0, bytesRead))
            }
            targetFile.unzipTo(datasetDir)
          case conn =>
            throw new RuntimeException(
              s"Unable to download OWASP Java 1.2 from $benchmarkUrl. Status code ${conn.getResponseCode}"
            )
        }
      } finally {
        connection.foreach(_.disconnect())
        targetFile.delete(swallowIOExceptions = true)
      }
    }
    benchmarkBaseDir
  }

  override def findings(testName: String)(implicit cpg: Cpg): List[Finding] = {
    cpg.findings.filter(_.keyValuePairs.exists(_.value == testName)).l
  }

  override def compare(testName: String, flowExists: Boolean)(implicit cpg: Cpg): TestOutcome.Value = {
    findings(testName) match {
      case Nil if flowExists => TestOutcome.FN
      case Nil               => TestOutcome.TN
      case xs if flowExists  => TestOutcome.TP
      case _                 => TestOutcome.FP
    }
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
