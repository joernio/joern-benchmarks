package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JVMBytecodeCpgCreator, JavaCpgCreator, JavaSrcCpgCreator}
import io.joern.benchmarks.passes.FindingsPass
import io.joern.benchmarks.runner.*
import io.joern.x2cpg.utils.ExternalCommand
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Finding}
import io.shiftleft.semanticcpg.language.*
import org.slf4j.LoggerFactory

import java.net.{URI, URL}
import scala.collection.mutable
import scala.util.{Failure, Success, Try, Using}

abstract class SecuribenchMicroRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with SingleFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = s"Securibench Micro v1.08 $creatorLabel"

  private val version     = "0.4.0"
  private val packageName = s"securibench-micro-$creatorLabel"

  override protected val benchmarkUrl: URL =
    URI(s"$baseDatasetsUrl/v$version/$packageName.zip").toURL

  override protected val benchmarkFileName: String = "securibench-micro-6a5a72488ea830d99f9464fc1f0562c4f864214b"
  override protected val benchmarkBaseDir: File    = datasetDir / benchmarkFileName

  private val apacheJdo = URI("https://repo1.maven.org/maven2/javax/jdo/jdo-api/3.1/jdo-api-3.1.jar").toURL

  override def initialize(): Try[File] = Try {
    downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)
//    downloadFile(apacheJdo, benchmarkBaseDir / "lib" / "jdo-api-3.1.jar")
//    if (
//      creatorLabel == "JAVA" && (benchmarkBaseDir / "classes")
//        .walk()
//        .count(_.`extension`.contains(".class")) < 1
//    ) {
//      val sourceFiles = (benchmarkBaseDir / "src")
//        .walk()
//        .filter(f => f.isRegularFile && f.`extension`.contains(".java"))
//        .map(f => f.pathAsString.stripPrefix(s"${benchmarkBaseDir.pathAsString}${java.io.File.separator}"))
//        .mkString(" ")
//      val command =
//        Seq(
//          "javac",
//          "-cp",
//          "'.:lib/cos.jar:lib/j2ee.jar:lib/java2html.jar:lib/jdo-api-3.1.jar;'",
//          "-d",
//          "classes",
//          sourceFiles
//        ).mkString(" ")
//      ExternalCommand.run(command, benchmarkBaseDir.pathAsString) match {
//        case Failure(exception) =>
//          logger.error(s"Exception encountered while compiling source code with: '$command'")
//          throw exception
//        case Success(_) => logger.info(s"Successfully compiled $benchmarkName")
//      }
//    }
    benchmarkBaseDir
  }

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if a the sink is tainted.
    */
  protected def getExpectedTestOutcomes: Map[String, Boolean] = {

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

}
