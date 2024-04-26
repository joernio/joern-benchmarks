package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.JavaSrcCpgCreator
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.utils.ExternalCommand
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import org.slf4j.LoggerFactory

import scala.collection.mutable
import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

class SecuribenchMicroRunner(datasetDir: File)
    extends BenchmarkRunner(datasetDir)
    with ArchiveDownloader
    with JavaSrcCpgCreator {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = "Securibench Micro v1.08"

  override protected val benchmarkUrl: URL = URI(
    "https://github.com/too4words/securibench-micro/archive/6a5a724.zip"
  ).toURL
  override protected val benchmarkFileName: String = "securibench-micro-6a5a72488ea830d99f9464fc1f0562c4f864214b"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  override def initialize(): Try[File] = downloadBenchmark

  override def findings(testName: String)(implicit cpg: Cpg): List[Finding] = {
    cpg.findings.filter(_.keyValuePairs.exists(_.value == testName)).l
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runSecuribenchMicro()
    }
  }

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if a the sink is tainted.
    */
  private def getExpectedTestOutcomes: Map[String, Boolean] = {

    def splitLine(line: String): Option[String] = {
      line.split(':').toList match {
        case fileName :: lineNo :: _ if lineNo.toIntOption.isDefined =>
          Option(s"${fileName.split(java.io.File.separator).last.stripSuffix(".java")}:${lineNo.toInt}")
        case _ =>
          logger.error(s"Unable to determine filename and line number from $line")
          None
      }
    }

    val cwd           = benchmarkBaseDir.pathAsString
    val sinkLocations = mutable.Map.empty[String, Boolean]

    ExternalCommand.run("grep -rn '/* BAD' .", cwd) match {
      case Failure(exception) => logger.error(s"Unable to query for tainted sinks in $cwd")
      case Success(output) =>
        output.flatMap(splitLine).foreach { x => sinkLocations.put(x, true) }
    }
    ExternalCommand.run("grep -rn '/* OK' .", cwd) match {
      case Failure(exception) => logger.error(s"Unable to query for tainted sinks in $cwd")
      case Success(output) =>
        output.flatMap(splitLine).foreach { x => sinkLocations.put(x, false) }
    }
    sinkLocations.toMap
  }

  private def runSecuribenchMicro(): Result = {
    createCpg(benchmarkBaseDir) match {
      case Failure(exception) =>
        logger.error("Unable to generate CPG for OWASP benchmark")
        Result()
      case Success(cpg) =>
        implicit val cpgImpl: Cpg = cpg
        val expectedTestOutcomes  = getExpectedTestOutcomes
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
