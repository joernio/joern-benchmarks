package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import org.slf4j.LoggerFactory

import java.net.{URI, URL}
import scala.collection.mutable
import scala.util.Try

abstract class SecuribenchMicroRunner(datasetDir: File, creatorLabel: String)
    extends BenchmarkRunner(datasetDir)
    with TaintAnalysisRunner
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

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.ZIP)

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if the sink is tainted.
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

    val cwd           = File(benchmarkBaseDir.pathAsString).toJava
    val sinkLocations = mutable.Map.empty[String, Boolean]

    runCmd(Seq("grep", "-rn", "/* BAD", "."), cwd) match {
      case RunOutput(0, stdOut, _, _) =>
        stdOut.flatMap(splitLine).foreach { x => sinkLocations.put(x, true) }
      case RunOutput(_, _, stdErr, _) =>
        logger.error(s"Unable to 'grep' for tainted sinks in $cwd: ${stdErr.mkString("\n")}")
    }
    runCmd(Seq("grep", "-rn", "/* OK", "."), cwd) match {
      case RunOutput(0, stdOut, _, _) =>
        stdOut.flatMap(splitLine).foreach { x => sinkLocations.put(x, false) }
      case RunOutput(_, _, stdErr, _) =>
        logger.error(s"Unable to 'grep' for tainted sinks in $cwd: ${stdErr.mkString("\n")}")
    }

    sinkLocations.put("Aliasing3:46", false)      // This is incorrectly tagged as vulnerable in the dataset
    sinkLocations.put("Basic24:46", true)         // This does not have a comment but the vulnerability is present
    sinkLocations.put("Collections8:51", false)   //  This is incorrectly tagged as vulnerable in the dataset
    sinkLocations.put("Collections13:52", false)  //  This is incorrectly tagged as vulnerable instead of next line
    sinkLocations.put("Collections13:54", true)   //  This is incorrectly tagged ok
    sinkLocations.put("Datastructures1:58", true) //  This is incorrectly tagged ok

    sinkLocations.toMap
  }

}
