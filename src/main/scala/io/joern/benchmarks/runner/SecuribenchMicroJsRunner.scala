package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import org.slf4j.LoggerFactory

import java.net.{URI, URL}
import scala.collection.mutable
import scala.util.Try

abstract class SecuribenchMicroJsRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with TaintAnalysisRunner
    with MultiFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = s"securibench-micro.js v1.0.2 $creatorLabel"

  private val packageName = "securibench-micro-js"

  override protected val benchmarkUrls: Map[String, URL] = Map(
    "securibench-micro-js" -> URI(s"$baseDatasetsUrl/$benchmarksVersion/$packageName.zip").toURL
  )

  override protected val benchmarkDirName: String = packageName
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  protected def fileNameToTestName(fileName: String): String =
    fileName.split(java.io.File.separator).takeRight(2).map(_.stripSuffix(".js")).mkString

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if the sink is tainted.
    */
  protected def getExpectedTestOutcomes: Map[String, Boolean] = {

    def splitLine(line: String): Option[String] = {
      line.split(':').toList match {
        case fileName :: lineNo :: _ if lineNo.toIntOption.isDefined && fileName.endsWith(".js") =>
          val testName = fileNameToTestName(fileName)
          Option(s"$testName:${lineNo.toInt}")
        case _ =>
          logger.error(s"Unable to determine filename and line number from $line")
          None
      }
    }

    val cwd           = File(benchmarkBaseDir.pathAsString).toJava
    val sinkLocations = mutable.Map.empty[String, Boolean]

    runCmd(Seq("grep", "-rn", "'// BAD'", ".").mkString(" "), cwd) match {
      case RunOutput(0, stdOut, _) =>
        stdOut.flatMap(splitLine).foreach { x => sinkLocations.put(x, true) }
      case RunOutput(_, _, stdErr) =>
        logger.error(s"Unable to 'grep' for tainted sinks in $cwd: ${stdErr.mkString("\n")}")
    }
    runCmd(Seq("grep", "-rn", "'// OK'", ".").mkString(" "), cwd) match {
      case RunOutput(0, stdOut, _) =>
        stdOut.flatMap(splitLine).foreach { x => sinkLocations.put(x, false) }
      case RunOutput(_, _, stdErr) =>
        logger.error(s"Unable to 'grep' for tainted sinks in $cwd: ${stdErr.mkString("\n")}")
    }

    sinkLocations.toMap
  }

}