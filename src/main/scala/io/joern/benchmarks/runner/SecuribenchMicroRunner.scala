package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner
import io.joern.x2cpg.utils.ExternalCommand
import org.slf4j.LoggerFactory

import java.net.{URI, URL}
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

abstract class SecuribenchMicroRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with MultiFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = s"Securibench Micro v1.08 $creatorLabel"

  private val packageName =
    if (creatorLabel == "JAVA") {
      s"securibench-micro-JAVA"
    } else {
      s"securibench-micro-JAVASRC"
    }

  override protected val benchmarkUrls: Map[String, URL] = Map(
    "securibench-micro" -> URI(s"$baseDatasetsUrl/$benchmarksVersion/$packageName.zip").toURL
  )

  override protected val benchmarkDirName: String = packageName
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  private val apacheJdo = URI("https://repo1.maven.org/maven2/javax/jdo/jdo-api/3.1/jdo-api-3.1.jar").toURL

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if a the sink is tainted.
    */
  protected def getExpectedTestOutcomes: Map[String, Boolean] = {

    def splitLine(line: String): Option[String] = {
      line.split(':').toList match {
        case fileName :: lineNo :: _ if lineNo.toIntOption.isDefined && fileName.endsWith(".java") =>
          Option(s"${fileName.split(java.io.File.separator).last.stripSuffix(".java")}:${lineNo.toInt}")
        case _ =>
          logger.error(s"Unable to determine filename and line number from $line")
          None
      }
    }

    val cwd           = benchmarkBaseDir.pathAsString
    val sinkLocations = mutable.Map.empty[String, Boolean]

    ExternalCommand.run(Seq("grep", "-rn", "'/* BAD'", "."), cwd) match {
      case ExternalCommand.ExternalCommandResult(0, stdOut, _) =>
        stdOut.flatMap(splitLine).foreach { x => sinkLocations.put(x, false) }
      case ExternalCommand.ExternalCommandResult(_, _, stdErr) =>
        logger.error(s"Unable to 'grep' for tainted sinks in $cwd: $stdErr")
    }
    ExternalCommand.run(Seq("grep", "-rn", "'/* OK'", "."), cwd) match {
      case ExternalCommand.ExternalCommandResult(0, stdOut, _) =>
        stdOut.flatMap(splitLine).foreach { x => sinkLocations.put(x, false) }
      case ExternalCommand.ExternalCommandResult(_, _, stdErr) =>
        logger.error(s"Unable to 'grep' for tainted sinks in $cwd: $stdErr")
    }
    sinkLocations.toMap
  }

}
