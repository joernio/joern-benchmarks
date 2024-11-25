package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.TestEntry
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.CodeQLSimpleResult
import io.joern.benchmarks.runner.{FindingInfo, SecuribenchMicroJsRunner, SecuribenchMicroRunner}

import scala.util.{Failure, Success}

class SecuribenchMicroJsCodeQLRunner(datasetDir: File)
    extends SecuribenchMicroJsRunner(datasetDir, CodeQLBenchmarkRunner.CreatorLabel)
    with CodeQLBenchmarkRunner {

  override protected def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cqlResults.findings
      .filter { case CodeQLSimpleResult(filename, lineNumber) =>
        val testNameFromFile = fileNameToTestName(filename)
        testNameFromFile.startsWith(name) && lineNumber == lineNo.toInt
      }
      .map(_ => FindingInfo())
  }

  override def runIteration: Domain.BaseResult = {
    val rules = getRules("SecuribenchMicroJs").toList
    runScan(benchmarkBaseDir / "securibench-micro.js-1.0.2", "javascript", rules) match {
      case Failure(exception) =>
        logger.error(s"Error encountered while running `codeql` on $benchmarkName", exception)
        Domain.TaintAnalysisResult()
      case Success(codeQlResults) =>
        setResults(codeQlResults)
        val testResults = getExpectedTestOutcomes
          .map { case (testName, outcome) =>
            TestEntry(testName, compare(testName, outcome))
          }
          .toList
          .sortBy(_.testName)
        Domain.TaintAnalysisResult(testResults)
    }
  }

}
