package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.runner.BenchmarkRunner
import io.joern.benchmarks.runner.semgrep.SemGrepBenchmarkRunner.SemGrepFindings
import io.joern.x2cpg.utils.ExternalCommand
import upickle.default.*

import scala.util.{Failure, Success, Try}

/** Facilitates the execution and parsing of SemGrep results.
  */
trait SemGrepBenchmarkRunner { this: BenchmarkRunner =>

  protected def runScan(inputDir: File): Try[SemGrepFindings] = {
    val command = Seq("semgrep", "scan", "--no-git-ignore", s"--json", "-q", inputDir.pathAsString).mkString(" ")
    ExternalCommand.run(command, ".") match {
      case Failure(exception) =>
        logger.error("Error encountered while executing SemGrep scan! Make sure `semgrep` is installed and logged in.")
        Failure(exception)
      case Success(lines) =>
        Try(read[SemGrepFindings](lines.mkString("\n")))
    }
  }

}

object SemGrepBenchmarkRunner {

  val CreatorLabel: String = "SEMGREP"

  implicit val semFindingsRw: ReadWriter[SemGrepFindings] = readwriter[ujson.Value]
    .bimap[SemGrepFindings](
      x => ujson.Null, // we do not deserialize
      json => SemGrepFindings(read[Seq[SemGrepResult]](json.obj("results")).filter(_.hasTrace))
    )

  case class SemGrepFindings(results: Seq[SemGrepResult])

  case class SemGrepResult(start: SemGrepLoc, end: SemGrepLoc, path: String, extra: SemGrepExtra) derives ReadWriter {
    def hasTrace: Boolean = extra.dataflowTrace.isDefined
  }

  case class SemGrepLoc(col: Int, line: Int) derives ReadWriter

  implicit val semGrepExtraRw: ReadWriter[SemGrepExtra] = readwriter[ujson.Value]
    .bimap[SemGrepExtra](
      x => ujson.Null, // we do not deserialize
      json => SemGrepExtra(json.obj.get("dataflow_trace").map(x => read[SemGrepTrace](x)))
    )

  case class SemGrepExtra(@upickle.implicits.key("dataflow_trace") dataflowTrace: Option[SemGrepTrace])

  case class SemGrepTrace(
    @upickle.implicits.key("taint_sink") taintSink: (String, (SemGrepTraceLoc, String)),
    @upickle.implicits.key("taint_source") taintSource: (String, (SemGrepTraceLoc, String))
  ) derives ReadWriter

  case class SemGrepTraceLoc(start: SemGrepLoc, end: SemGrepLoc, path: String) derives ReadWriter

}
