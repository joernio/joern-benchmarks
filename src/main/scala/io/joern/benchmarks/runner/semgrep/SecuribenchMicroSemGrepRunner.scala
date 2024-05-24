package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.runner.SecuribenchMicroRunner
import io.shiftleft.codepropertygraph.generated.nodes.Finding

import scala.util.{Failure, Success}

class SecuribenchMicroSemGrepRunner(datasetDir: File)
    extends SecuribenchMicroRunner(datasetDir, SemGrepBenchmarkRunner.CreatorLabel)
    with SemGrepBenchmarkRunner {

  override protected def findings(testName: String): List[Finding] = Nil

  override def run(): Domain.Result = {
    runScan(benchmarkBaseDir) match {
      case Failure(exception) => logger.error(s"Error encountered while running `semgrep` on $benchmarkName", exception)
      case Success(semgrepResults) =>
        println(semgrepResults)
    }
    Domain.Result()
  }

}
