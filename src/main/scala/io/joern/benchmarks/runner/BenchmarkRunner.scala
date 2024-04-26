package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.Domain.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import org.slf4j.LoggerFactory

import java.net.URL
import scala.util.{Failure, Success, Try}

/** A process that runs a benchmark.
  */
trait BenchmarkRunner(protected val datasetDir: File) {

  private val logger = LoggerFactory.getLogger(getClass)

  val benchmarkName: String

  /** Create and setup the benchmark if necessary.
    *
    * @return
    *   the directory where the benchmark is set up if successful.
    */
  protected def initialize(): Try[File]

  /** The findings for the given test.
    * @return
    *   a list of findings, if any.
    */
  protected def findings(testName: String)(implicit cpg: Cpg): List[Finding]

  /** Compares the test expectations against the actual findings.
    * @param testName
    *   the test name, from where the expected result can be retrieved.
    * @param flowExists
    *   the expected outcome for the test.
    */
  protected def compare(testName: String, flowExists: Boolean)(implicit cpg: Cpg): TestOutcome.Value = {
    findings(testName) match {
      case Nil if flowExists => TestOutcome.FN
      case Nil               => TestOutcome.TN
      case xs if flowExists  => TestOutcome.TP
      case _                 => TestOutcome.FP
    }
  }

  /** The main benchmark runner entrypoint
    */
  def run(): Result

}
