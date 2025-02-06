package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.runner.RunOutput
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{BaseResult, TaintAnalysisResult, TestEntry}
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.CodeQLSimpleResult
import io.joern.benchmarks.runner.{FindingInfo, IchnaeaRunner, ThoratPythonRunner}

import scala.util.{Failure, Success}

class IchnaeaCodeQLRunner(datasetDir: File)
    extends IchnaeaRunner(datasetDir, CodeQLBenchmarkRunner.CreatorLabel)
    with CodeQLBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    cqlResults.findings.map(_ => FindingInfo()) // Simply, if there are findings then there is a positive
  }

  override def runIteration: BaseResult = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        TaintAnalysisResult()
      case Success(benchmarkDir) =>
        runIchnaea()
    }
  }

  private def runIchnaea(): BaseResult = {
    val outcomes = getExpectedTestOutcomes
    val rules    = getRules("Ichnaea").toList
    packageNames
      .map { packageName =>
        val inputDir = benchmarkBaseDir / packageName
        runScan(inputDir / "package", "javascript", rules) match {
          case Failure(exception) =>
            logger.error(s"Error encountered while running `codeql` on $benchmarkName/$packageName", exception)
            TaintAnalysisResult()
          case Success((codeQlResults, memory)) =>
            setResults(codeQlResults)
            TaintAnalysisResult(
              TestEntry(packageName, compare(packageName, outcomes(packageName))) :: Nil,
              memory = memory
            )
        }
      }
      .foldLeft(TaintAnalysisResult())(_ ++ _)
  }
}
