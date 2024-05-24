package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JavaCpgCreator, JsSrcCpgCreator}
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.benchmarks.runner.{BenchmarkRunner, CompressionTypes, CpgBenchmarkRunner, SingleFileDownloader}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.X2CpgFrontend
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import org.slf4j.LoggerFactory

import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

abstract class OWASPJavaRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with SingleFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = s"OWASP Java v1.2 $creatorLabel"

  override protected val benchmarkUrl: URL = URI(
    "https://github.com/OWASP-Benchmark/BenchmarkJava/archive/refs/tags/1.2beta.zip"
  ).toURL
  override protected val benchmarkFileName: String = "BenchmarkJava-1.2beta"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  private val apacheJdo = URI("https://repo1.maven.org/maven2/javax/jdo/jdo-api/3.1/jdo-api-3.1.jar").toURL

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  protected def getExpectedTestOutcomes: Map[String, Boolean] = {
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

}
