package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JVMBytecodeCpgCreator, JavaCpgCreator, JavaSrcCpgCreator}
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.utils.ExternalCommand
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Finding}
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import org.slf4j.LoggerFactory

import scala.collection.mutable
import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import java.nio.file.Path
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

class SecuribenchMicroRunner(datasetDir: File, cpgCreator: JavaCpgCreator[?])
    extends BenchmarkRunner(datasetDir)
    with FileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = s"Securibench Micro v1.08 ${cpgCreator.frontend}"

  override protected val benchmarkUrl: URL = URI(
    "https://github.com/too4words/securibench-micro/archive/6a5a724.zip"
  ).toURL
  override protected val benchmarkFileName: String = "securibench-micro-6a5a72488ea830d99f9464fc1f0562c4f864214b"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  private val apacheJdo = URI("https://repo1.maven.org/maven2/javax/jdo/jdo-api/3.1/jdo-api-3.1.jar").toURL

  override def initialize(): Try[File] = Try {
    downloadBenchmarkAndUnzip
    downloadFile(apacheJdo, benchmarkBaseDir / "lib" / "jdo-api-3.1.jar")
    if (
      cpgCreator.isInstanceOf[JVMBytecodeCpgCreator] && (benchmarkBaseDir / "classes")
        .walk()
        .count(_.`extension`.contains(".class")) < 1
    ) {
      val sourceFiles = (benchmarkBaseDir / "src")
        .walk()
        .filter(f => f.isRegularFile && f.`extension`.contains(".java"))
        .map(f => f.pathAsString.stripPrefix(s"${benchmarkBaseDir.pathAsString}${java.io.File.separator}"))
        .mkString(" ")
      val command =
        Seq(
          "javac",
          "-cp",
          "'.:lib/cos.jar:lib/j2ee.jar:lib/java2html.jar:lib/jdo-api-3.1.jar;'",
          "-d",
          "classes",
          sourceFiles
        ).mkString(" ")
      ExternalCommand.run(command, benchmarkBaseDir.pathAsString) match {
        case Failure(exception) =>
          logger.error(s"Exception encountered while compiling source code with: '$command'")
          throw exception
        case Success(_) => logger.info(s"Successfully compiled $benchmarkName")
      }
    }
    benchmarkBaseDir
  }

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
    val inputDir = cpgCreator match {
      case creator: JVMBytecodeCpgCreator => benchmarkBaseDir / "classes"
      case creator: JavaSrcCpgCreator     => benchmarkBaseDir / "src"
    }
    cpgCreator.createCpg(inputDir, cpg => SecuribenchMicroSourcesAndSinks(cpg)) match {
      case Failure(exception) =>
        logger.error(s"Unable to generate CPG for $benchmarkName", exception)
        Result()
      case Success(cpg) =>
        Using.resource(cpg) { cpg =>
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

  class SecuribenchMicroSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {
    override def sources: Traversal[CfgNode] =
      cpg.parameter.and(_.index(1), _.method.name("foo"), _.typeFullNameExact("java.lang.Object"))
  }

}
