package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.runner.BenchmarkRunner
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner.SemGrepFindings
import io.joern.x2cpg.utils.ExternalCommand
import upickle.default.*

import scala.util.{Failure, Success, Try, Using}

/** Facilitates the execution and parsing of SemGrep results.
  */
trait SemgrepBenchmarkRunner { this: BenchmarkRunner =>

  private var resultsOpt: Option[SemGrepFindings] = None

  protected def setResults(results: SemGrepFindings): Unit = {
    resultsOpt = Option(results)
  }

  protected def sgResults: SemGrepFindings = resultsOpt match {
    case Some(results) => results
    case None          => throw new RuntimeException("No results have been set!")
  }

  protected def runScan(
    inputDir: File,
    customCommands: Seq[String] = Seq.empty,
    customRules: Seq[String] = Seq.empty
  ): Try[SemGrepFindings] = {
    if (customRules.nonEmpty) {
      val tmp = File
        .newTemporaryFile(prefix = "joern-benchmarks-semgrep", suffix = ".yaml")
        .writeText("rules:\n")
        .deleteOnExit(swallowIOExceptions = true)
      customRules.map(rule => tmp.appendText(s"$rule\n"))
      runScan(inputDir, customCommands, Option(tmp))
    } else {
      runScan(inputDir, customCommands, None)
    }
  }

  protected def runScan(
    inputDir: File,
    customCommands: Seq[String],
    customRuleFile: Option[File]
  ): Try[SemGrepFindings] = recordTime(() => {
    val customRulePath = customRuleFile match {
      case Some(f) => s"--config ${f.pathAsString}"
      case None    => ""
    }
    val command =
      (Seq(
        "semgrep",
        "scan",
        "--no-git-ignore",
        "--json",
        "--config auto",
        "-q"
      ) ++ customCommands :+ customRulePath :+ inputDir.pathAsString)
        .mkString(" ")
    ExternalCommand.run(command, inputDir.pathAsString) match {
      case Failure(exception) =>
        logger.error("Error encountered while executing SemGrep scan! Make sure `semgrep` is installed and logged in.")
        Failure(exception)
      case Success(lines) =>
        Try(read[SemGrepFindings](lines.mkString("\n")))
    }
  })

  protected def getRules(ruleName: String): Option[File] = {
    Option(getClass.getResourceAsStream(s"/semgrep/$ruleName.yaml")) match {
      case Some(res) =>
        Using.resource(res) { is =>
          Option {
            File
              .newTemporaryFile("joern-benchmarks-semrep-", ".yaml")
              .deleteOnExit(swallowIOExceptions = true)
              .writeByteArray(is.readAllBytes())
          }
        }
      case None =>
        logger.error(s"Unable to fetch Semgrep rules for $benchmarkName")
        None
    }
  }

}

object SemgrepBenchmarkRunner {

  val CreatorLabel: String = "SEMGREP"

  implicit val semFindingsRw: ReadWriter[SemGrepFindings] = readwriter[ujson.Value]
    .bimap[SemGrepFindings](
      x => ujson.Null, // we do not deserialize
      json => SemGrepFindings(read[Seq[SemGrepResult]](json.obj("results")))
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
