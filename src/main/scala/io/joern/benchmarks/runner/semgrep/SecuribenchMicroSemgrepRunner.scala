package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{Result, TestEntry}
import io.joern.benchmarks.runner.{FindingInfo, SecuribenchMicroRunner}
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner.SemGrepTrace

import scala.util.{Failure, Success, Using}

class SecuribenchMicroSemgrepRunner(datasetDir: File)
    extends SecuribenchMicroRunner(datasetDir, SemgrepBenchmarkRunner.CreatorLabel)
    with SemgrepBenchmarkRunner {

  override protected def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    sgResults.results
      .flatMap(_.extra.dataflowTrace)
      .filter { case SemGrepTrace((_, (sinkLoc, _)), _) =>
        sinkLoc.path.stripSuffix(".java").endsWith(name) && sinkLoc.end.line == lineNo.toInt
      }
      .map(_ => FindingInfo())
      .toList
  }

  override def run(): Domain.Result = {
    val rules = getRules("SecuribenchMicroRules")
    runScan(benchmarkBaseDir, Seq.empty, rules) match {
      case Failure(exception) =>
        logger.error(s"Error encountered while running `semgrep` on $benchmarkName", exception)
        Domain.Result()
      case Success(semgrepResults) =>
        setResults(semgrepResults)
        val expectedTestOutcomes = getExpectedTestOutcomes
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
