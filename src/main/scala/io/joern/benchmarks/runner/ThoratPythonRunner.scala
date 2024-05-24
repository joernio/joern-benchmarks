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
import scala.collection.mutable
import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

class ThoratPythonRunner(datasetDir: File, cpgCreator: PythonCpgCreator[?])
    extends BenchmarkRunner(datasetDir)
      with CpgBenchmarkRunner
    with SingleFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  private val version        = "0.0.4"
  override val benchmarkName = s"Thorat Python v$version ${cpgCreator.frontend}"

  override protected val benchmarkUrl: URL = URI(
    s"https://github.com/DavidBakerEffendi/benchmark-for-taint-analysis-tools-for-python/archive/refs/tags/v$version.zip"
  ).toURL
  override protected val benchmarkFileName: String = s"benchmark-for-taint-analysis-tools-for-python-$version"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  override def initialize(): Try[File] = {
    if (!benchmarkBaseDir.exists) downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)
    else Try(benchmarkBaseDir)
  }

  override def findings(testName: String): List[Finding] = {
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

  private lazy val testMetaData: Map[String, TAF] = {
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

  private def getExpectedTestOutcomes: Map[String, Boolean] = {
    testMetaData.flatMap { case (filename, metaData) =>
      metaData.findings.map(f => s"$filename:${f.sink.lineNo}" -> !f.isNegative)
    }
  }

  private def runThorat(): Result = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    val result = (benchmarkBaseDir / "tests").list
      .filter(_.isDirectory)
      .map { testDir =>
        cpgCreator.createCpg(testDir, cpg => ThoratSourcesAndSinks(cpg)) match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName benchmark")
            Result()
          case Success(cpg) =>
            Using.resource(cpg) { cpg =>
              setCpg(cpg)
              val testName              = testDir.name
              // TODO: Check for negatives/excluded findings
              val results = expectedTestOutcomes.collect {
                case (testFullName, outcome) if testFullName.startsWith(testName) =>
                  TestEntry(testFullName, compare(testFullName, outcome))
              }.toList
              Result(results)
            }
        }
      }
      .foldLeft(Result())(_ ++ _)
    val leftoverResults =
      expectedTestOutcomes
        .filter { case (name, outcome) => !result.entries.exists(x => name.startsWith(x.testName)) }
        .map {
          case (name, false) => TestEntry(name, TestOutcome.TN)
          case (name, true)  => TestEntry(name, TestOutcome.FN)
        }
        .l
    result.copy(entries = result.entries ++ leftoverResults)
  }

  class ThoratSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    private val candidates: List[TAF] = cpg.file.name.flatMap(testMetaData.get).l

    private val sourceCandidates: List[TAFStatement] =
      candidates
        .flatMap(_.findings)
        .map(_.source)

    private val sinkCandidates: List[TAFStatement] =
      candidates
        .flatMap(_.findings)
        .map(_.sink)

    override def sources: Iterator[CfgNode] = {
      val filenameString = candidates.map(_.fileName).mkString(",")
      sourceCandidates.flatMap { case TAFStatement(_, methodName, _, lineNo, targetName, _) =>
        val methodTrav = cpg.method.nameExact(methodName).l
        if (methodTrav.isEmpty) {
          logger.warn(s"Unable to match source method for '$methodName' among [$filenameString]")
          Iterator.empty
        } else {
          val targetTrav = targetName match {
            case "" => methodTrav.call.assignment.source.lineNumber(lineNo).l
            case callName =>
              methodTrav.call.and(_.lineNumber(lineNo), _.or(_.nameExact(callName), _.code(s".*$callName"))).l
          }
          if (targetTrav.isEmpty) {
            logger.warn(s"Unable to match source target for '$targetName' among [$filenameString]")
          }
          targetTrav
        }
      }.iterator
    }

    override def sinks: Iterator[CfgNode] = {
      val filenameString = candidates.map(_.fileName).mkString(",")
      sinkCandidates.flatMap { case TAFStatement(_, methodName, _, lineNo, targetName, _) =>
        val methodTrav = cpg.method.nameExact(methodName).l
        if (methodTrav.isEmpty) {
          logger.warn(s"Unable to match sink method for '$methodName' among [$filenameString]")
          Iterator.empty
        } else {
          val targetTrav = targetName match {
            case ""       => methodTrav.call.assignment.source.lineNumber(lineNo).l
            case callName =>
              // Tags calls and method refs (which would be identifiers for external method refs)
              methodTrav.call.argument.isIdentifier.and(_.lineNumber(lineNo), _.nameExact(callName)).l ++
                methodTrav.call
                  .and(_.lineNumber(lineNo), _.or(_.nameExact(callName), _.code(s".*$callName")))
                  .argument
                  .argumentIndexGt(0)
                  .l
          }
          if (targetTrav.isEmpty) {
            logger.warn(s"Unable to match sink target for '$targetName' among [$filenameString]")
          }
          targetTrav
        }
      }.iterator
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
