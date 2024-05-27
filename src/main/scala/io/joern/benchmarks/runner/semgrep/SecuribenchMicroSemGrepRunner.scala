package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{Result, TestEntry}
import io.joern.benchmarks.runner.{FindingInfo, SecuribenchMicroRunner}
import io.joern.benchmarks.runner.semgrep.SemGrepBenchmarkRunner.SemGrepTrace
import io.shiftleft.codepropertygraph.generated.nodes.Finding

import scala.util.{Failure, Success}

class SecuribenchMicroSemGrepRunner(datasetDir: File)
    extends SecuribenchMicroRunner(datasetDir, SemGrepBenchmarkRunner.CreatorLabel)
    with SemGrepBenchmarkRunner {

  override protected def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    sgResults.results
      .filter { result =>
        result.path.stripSuffix(".java").endsWith(name)
      }
      .flatMap(_.extra.dataflowTrace)
      .filter { case SemGrepTrace((_, (sinkLocation, sinkCode)), _) =>
        sinkLocation.start.line == lineNo.toInt
      }
      .map(_ => FindingInfo())
      .toList
  }

  override def run(): Domain.Result = {
    runScan(benchmarkBaseDir) match {
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
