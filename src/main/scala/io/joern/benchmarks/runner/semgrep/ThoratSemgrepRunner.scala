package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.runner.*
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner.SemGrepTrace

import scala.util.{Failure, Success}

class ThoratSemgrepRunner(datasetDir: File)
    extends ThoratPythonRunner(datasetDir, SemgrepBenchmarkRunner.CreatorLabel)
    with SemgrepBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    sgResults.results
      .flatMap(_.extra.dataflowTrace)
      .filter { case SemGrepTrace((_, (sinkLoc, _)), _) =>
        sinkLoc.path.endsWith(name) && sinkLoc.start.line == lineNo.toInt
      }
      .map(_ => FindingInfo())
      .toList
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

  private def runThorat(): Result = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    val rules                = getRules("ThoratRules")
    runScan(benchmarkBaseDir / "tests", Seq("--include=*.py"), rules) match {
      case Failure(exception) =>
        logger.error(s"Error encountered while running `semgrep` on $benchmarkName", exception)
        Domain.Result()
      case Success(semgrepResults) =>
        setResults(semgrepResults)
        val testResults = expectedTestOutcomes
          .map { case (testName, outcome) =>
            TestEntry(testName, compare(testName, outcome))
          }
          .toList
          .sortBy(_.testName)
        Domain.Result(testResults)
    }
  }

}
