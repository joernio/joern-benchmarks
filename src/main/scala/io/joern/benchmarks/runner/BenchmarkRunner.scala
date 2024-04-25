package io.joern.benchmarks.runner

import io.shiftleft.codepropertygraph.generated.nodes.Finding
import better.files.File
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import java.net.URL

/** A process that runs a benchmark.
  */
trait BenchmarkRunner(datasetDir: File) {

  private val logger = LoggerFactory.getLogger(getClass)

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
  protected def findings(testInput: File): List[Finding]

  /** Compares the test expectations against the actual findings.
    * @param testName
    *   the test name, from where the expected result can be retrieved.
    * @param findings
    *   the actual findings after the scan.
    */
  protected def compare(testName: String, findings: List[Finding]): TestOutcome.Value

  /** The main benchmark runner entrypoint
    */
  def run(): Result

}

/** The result of a benchmark.
  */
case class Result(entries: List[TestEntry] = Nil)

/** A test and it's outcome.
  */
case class TestEntry(testName: String, outcome: TestOutcome.Value)

object TestOutcome extends Enumeration {

  /** False positive
    */
  val FP = Value

  /** True positive
    */
  val TP = Value

  /** True negative
    */
  val TN = Value

  /** False negative
    */
  val FN = Value

}
