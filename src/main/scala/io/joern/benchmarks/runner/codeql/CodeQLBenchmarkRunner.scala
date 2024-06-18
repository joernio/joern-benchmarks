package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.runner.BenchmarkRunner
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.{
  CodeQLFindings,
  CodeQLPhysicalLocation,
  CodeQLSimpleFindings,
  CodeQLSimpleResult
}
import io.joern.x2cpg.utils.ExternalCommand
import upickle.default.*

import scala.util.{Failure, Success, Try, Using}

/** Facilitates the execution and parsing of CodeQL results.
  */
trait CodeQLBenchmarkRunner { this: BenchmarkRunner =>

  private var resultsOpt: Option[CodeQLSimpleFindings] = None

  protected def setResults(results: CodeQLSimpleFindings): Unit = {
    resultsOpt = Option(results)
  }

  protected def cqlResults: CodeQLSimpleFindings = resultsOpt match {
    case Some(results) => results
    case None          => throw new RuntimeException("No results have been set!")
  }

  private def initializeDatabase(sourceRoot: File, language: String): Try[File] = {
    val tmpDir = File.newTemporaryDirectory("joern-benchmarks-codeql-db") deleteOnExit (swallowIOExceptions = true)
    val cmd = Seq(
      "codeql",
      "database",
      "create",
      tmpDir.pathAsString,
      s"--source-root=${sourceRoot.pathAsString}",
      s"--language=$language",
      "--overwrite",
      "--build-mode=none"
    ).mkString(" ")
    recordTime(() => { ExternalCommand.run(cmd, sourceRoot.parent.pathAsString) }) match {
      case Failure(exception) =>
        logger.error(
          "Error encountered while executing `codeql database create`! Make sure `codeql` is installed and there is an internet connection."
        )
        Failure(exception)
      case Success(_) =>
        Success(tmpDir)
    }
  }

  /** Attempts to install the query files as a query pack.
    * @return
    *   the query pack directory if successful.
    */
  private def installQuery(queryFiles: List[File], language: String): Try[File] = {
    val tmpDir =
      File.newTemporaryDirectory("joern-benchmarks-codeql-query-pack") deleteOnExit (swallowIOExceptions = true)
    queryFiles.foreach(f => f.copyTo(tmpDir / f.name))
    val qlPack = (tmpDir / "qlpack.yml")
      .createFile()
      .writeText(s"""
        |name: joern-benchmark-taint
        |version: 1.0.0
        |dependencies:
        |  codeql/$language-all: "*"
        |""".stripMargin)
    val cmd = Seq("codeql", "pack", "install", tmpDir.name).mkString(" ")
    ExternalCommand.run(cmd, tmpDir.parent.pathAsString) match {
      case Failure(exception) =>
        logger.error(
          "Error encountered while executing `codeql pack install`! Make sure `codeql` is installed and there is an internet connection."
        )
        Failure(exception)
      case Success(_) =>
        Success(tmpDir)
    }
  }

  protected def runScan(inputDir: File, language: String, queryFiles: List[File]): Try[CodeQLSimpleFindings] = {
    initializeDatabase(inputDir, language) match {
      case Failure(exception) => Failure(exception)
      case Success(databaseFile) =>
        installQuery(queryFiles, language) match {
          case Failure(exception) => Failure(exception)
          case Success(queryPackDir) =>
            val tmpFile =
              File.newTemporaryFile("joern-benchmarks-output", ".sarif") deleteOnExit (swallowIOExceptions = true)
            recordTime(() => {
              val command =
                Seq(
                  "codeql",
                  "database",
                  "analyze",
                  databaseFile.pathAsString,
                  "--format=sarif-latest",
                  s"--output=${tmpFile.pathAsString}",
                  queryPackDir.pathAsString
                ).mkString(" ")
              ExternalCommand.run(command, inputDir.pathAsString) match {
                case Failure(exception) =>
                  logger.error(
                    "Error encountered while executing `codeql database analyze`! Make sure `semgrep` is installed and logged in."
                  )
                  Failure(exception)
                case Success(_) =>
                  Try(read[CodeQLFindings](tmpFile.path)).map(simplifyResults)
              }
            })
        }
    }
  }

  protected def getRules(ruleName: String): Option[File] = {
    Option(getClass.getResourceAsStream(s"/codeql/$ruleName.ql")) match {
      case Some(res) =>
        Using.resource(res) { is =>
          Option {
            File
              .newTemporaryFile("joern-benchmarks-codeql-rule-", ".ql")
              .deleteOnExit(swallowIOExceptions = true)
              .writeByteArray(is.readAllBytes())
          }
        }
      case None =>
        logger.error(s"Unable to fetch Semgrep rules for $benchmarkName")
        None
    }
  }

  private def simplifyResults(results: CodeQLFindings): CodeQLSimpleFindings = {
    val simpleResults = results.runs
      .flatMap(_.results)
      .flatMap(_.codeFlows)
      .flatMap(_.threadFlows)
      .flatMap(_.locations.lastOption)
      .map(_.location)
      .map(_.physicalLocation)
      .map { case CodeQLPhysicalLocation(artifactLocation, region) =>
        CodeQLSimpleResult(artifactLocation.uri, region.startLine)
      }
      .toList
    CodeQLSimpleFindings(simpleResults)
  }

}

object CodeQLBenchmarkRunner {

  val CreatorLabel: String = "CODEQL"

  // A simple abstraction of the results
  case class CodeQLSimpleFindings(findings: List[CodeQLSimpleResult])

  case class CodeQLSimpleResult(filename: String, lineNumber: Int)

  // the JSON deserialization model
  case class CodeQLFindings(runs: Seq[CodeQLResults]) derives ReadWriter

  case class CodeQLResults(results: Seq[CodeQLResult]) derives ReadWriter

  implicit val codeQLResultRw: ReadWriter[CodeQLResult] = readwriter[ujson.Value]
    .bimap[CodeQLResult](
      x => ujson.Null, // we do not deserialize
      json => CodeQLResult(json.obj.get("codeFlows").map(x => read[Seq[CodeQLCodeFlow]](x)).getOrElse(Seq.empty))
    )

  case class CodeQLResult(codeFlows: Seq[CodeQLCodeFlow])

  case class CodeQLCodeFlow(threadFlows: Seq[CodeQLThreadFlow]) derives ReadWriter

  case class CodeQLThreadFlow(locations: Seq[CodeQLLocationWrapper]) derives ReadWriter

  case class CodeQLLocationWrapper(location: CodeQLLocation) derives ReadWriter

  case class CodeQLLocation(physicalLocation: CodeQLPhysicalLocation) derives ReadWriter

  case class CodeQLPhysicalLocation(artifactLocation: CodeQLArtifactLocation, region: CodeQLRegion) derives ReadWriter

  case class CodeQLArtifactLocation(uri: String) derives ReadWriter

  case class CodeQLRegion(startLine: Int) derives ReadWriter

}
