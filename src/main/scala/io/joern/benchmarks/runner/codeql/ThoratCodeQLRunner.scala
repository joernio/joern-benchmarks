package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{BaseResult, TaintAnalysisResult, TestEntry}
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.CodeQLSimpleResult
import io.joern.benchmarks.runner.{FindingInfo, ThoratPythonRunner}

import scala.util.{Failure, Success}

class ThoratCodeQLRunner(datasetDir: File, wholeProgram: Boolean)
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

  override def runIteration: BaseResult = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        TaintAnalysisResult()
      case Success(benchmarkDir) =>
        runThorat()
    }
  }

  private def runThorat(): BaseResult = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    val rules                = getRules("Thorat").toList
    val inputDir             = benchmarkBaseDir / "tests"
    if wholeProgram then setupWholeProgram(inputDir)
    try {
      runScan(inputDir, "python", rules) match {
        case Failure(exception) =>
          logger.error(s"Error encountered while running `codeql` on $benchmarkName", exception)
          Domain.TaintAnalysisResult()
        case Success((codeQlResults, memory)) =>
          setResults(codeQlResults)
          val testResults = expectedTestOutcomes
            .map { case (testName, outcome) =>
              TestEntry(testName, compare(testName, outcome))
            }
            .toList
            .sortBy(_.testName)
          Domain.TaintAnalysisResult(testResults, memory = memory)
      }
    } finally {
      if wholeProgram then cleanupWholeProgram(inputDir)
    }
  }
}
