package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.TestEntry
import io.joern.benchmarks.runner.{FindingInfo, SecuribenchMicroRunner}
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.CodeQLSimpleResult

import scala.util.{Failure, Success}

class SecuribenchMicroCodeQLRunner(datasetDir: File)
    extends SecuribenchMicroRunner(datasetDir, CodeQLBenchmarkRunner.CreatorLabel)
    with CodeQLBenchmarkRunner {

  override protected def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cqlResults.findings
      .filter { case CodeQLSimpleResult(filename, lineNumber) =>
        filename.stripSuffix(".java").endsWith(name) && lineNumber == lineNo.toInt
      }
      .map(_ => FindingInfo())
  }

  override def runIteration: Domain.BaseResult = {
    val rules = getRules("SecuribenchMicro").toList
    runScan(benchmarkBaseDir / "src", "java", rules) match {
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
