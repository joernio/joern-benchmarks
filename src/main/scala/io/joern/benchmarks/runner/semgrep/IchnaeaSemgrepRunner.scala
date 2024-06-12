package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.runner.*
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner.{SemGrepTrace, SemGrepTraceLoc}

import scala.util.{Failure, Success}

class IchnaeaSemgrepRunner(datasetDir: File)
    extends IchnaeaRunner(datasetDir, SemgrepBenchmarkRunner.CreatorLabel)
    with SemgrepBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    sgResults.results
      .filter(_.hasTrace)
      .flatMap(_.extra.dataflowTrace)
      .map(_ => FindingInfo())
      .toList
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runIchnaea()
    }
  }

  private def runIchnaea(): Result = {
    val outcomes = getExpectedTestOutcomes
    val rules    = getRules("IchnaeaRules")
    packageNames
      .map { packageName =>
        val inputDir = benchmarkBaseDir / packageName
        runScan(inputDir, Seq.empty, rules) match {
          case Failure(exception) =>
            logger.error(s"Error encountered while running `semgrep` on $benchmarkName/$packageName", exception)
            Result()
          case Success(semgrepResults) =>
            setResults(semgrepResults)
            Result(TestEntry(packageName, compare(packageName, outcomes(packageName))) :: Nil)
        }
      }
      .foldLeft(Result())(_ ++ _)
  }

}
