package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JavaCpgCreator, JsSrcCpgCreator, PythonCpgCreator}
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.X2CpgFrontend
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Finding}
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import org.slf4j.LoggerFactory
import upickle.default.*

import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

class ThoratPythonRunner(datasetDir: File, cpgCreator: PythonCpgCreator[?])
    extends BenchmarkRunner(datasetDir)
    with SingleFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  private val version        = "0.0.1"
  override val benchmarkName = s"Thorat Python v$version ${cpgCreator.frontend}"

  override protected val benchmarkUrl: URL = URI(
    s"https://github.com/DavidBakerEffendi/benchmark-for-taint-analysis-tools-for-python/archive/refs/tags/v$version.zip"
  ).toURL
  override protected val benchmarkFileName: String = s"benchmark-for-taint-analysis-tools-for-python-$version"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  override def findings(testName: String)(implicit cpg: Cpg): List[Finding] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cpg.findings
      .filter(_.keyValuePairs.keyExact(FindingsPass.FileName).exists(_.value == name))
      .filter(_.keyValuePairs.keyExact(FindingsPass.LineNo).exists(_.value == lineNo))
      .l
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runThorat()
    }
  }

  private lazy val testMetaData: Map[File, TAF] = {
    val testDir     = benchmarkBaseDir / "tests"
    val metaDataDir = benchmarkBaseDir / "tests_metadata"
    metaDataDir.list.flatMap { testDir =>
      val testName = testDir.name
      testDir.list
        .filter(f => f.`extension`.contains(".json") && f.name.endsWith("taf.json"))
        .map { testMetaDataFile =>
          val testMetaData = read[TAF](ujson.Readable.fromFile(testMetaDataFile.toJava))
          val targetFile   = testDir / testName / testMetaData.fileName
          targetFile -> testMetaData
        }
    }.toMap
  }

  private def getExpectedTestOutcomes: Map[String, Boolean] = {
    testMetaData.flatMap { case (file, metaData) =>
      metaData.findings.map(f => s"${file.name}:${f.sink.lineNo}" -> !f.isNegative)
    }
  }

  private def runThorat(): Result = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    (benchmarkBaseDir / "tests").list
      .filter(_.isDirectory)
      .map { testDir =>
        cpgCreator.createCpg(testDir, cpg => ThoratSourcesAndSinks(cpg)) match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName benchmark")
            Result()
          case Success(cpg) =>
            Using.resource(cpg) { cpg =>
              implicit val cpgImpl: Cpg = cpg
              val testName              = testDir.name
              val results = expectedTestOutcomes.collect {
                case (testFullName, outcome) if testFullName.startsWith(testName) =>
                  TestEntry(testFullName, compare(testFullName, outcome))
              }.toList
              Result(results)
            }
        }
      }
      .foldLeft(Result())(_ ++ _)
  }

  class ThoratSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    override def sources: Iterator[CfgNode] = {
      testMetaData.values
        .flatMap(_.findings)
        .map(_.source)
        .flatMap { case TAFStatement(_, methodName, _, lineNo, targetName, _) =>
          cpg.method.nameExact(methodName).call.and(_.lineNumber(lineNo), _.nameExact(targetName))
        }
        .iterator
    }

    override def sinks: Iterator[CfgNode] = {
      testMetaData.values
        .flatMap(_.findings)
        .map(_.sink)
        .flatMap { case TAFStatement(_, methodName, _, lineNo, targetName, _) =>
          cpg.method.nameExact(methodName).call.and(_.lineNumber(lineNo), _.nameExact(targetName)).argument
        }
        .iterator
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
