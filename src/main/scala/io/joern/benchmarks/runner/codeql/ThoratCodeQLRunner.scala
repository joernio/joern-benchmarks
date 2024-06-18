package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{Result, TestEntry}
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.CodeQLSimpleResult
import io.joern.benchmarks.runner.{FindingInfo, ThoratPythonRunner}

import scala.util.{Failure, Success}

class ThoratCodeQLRunner(datasetDir: File)
    extends ThoratPythonRunner(datasetDir, CodeQLBenchmarkRunner.CreatorLabel)
    with CodeQLBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cqlResults.findings
      .filter { case CodeQLSimpleResult(filename, lineNumber) =>
        filename.endsWith(name) && lineNo.toInt == lineNumber
      }
      .map(_ => FindingInfo())
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
    val rules                = getRules("Thorat").toList
    runScan(benchmarkBaseDir / "tests", "python", rules) match {
      case Failure(exception) =>
        logger.error(s"Error encountered while running `codeql` on $benchmarkName", exception)
        Domain.Result()
      case Success(codeQlResults) =>
        setResults(codeQlResults)
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
