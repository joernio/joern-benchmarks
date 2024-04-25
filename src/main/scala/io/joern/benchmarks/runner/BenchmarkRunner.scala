package io.joern.benchmarks.runner

import io.shiftleft.codepropertygraph.generated.nodes.Finding
import better.files.File
import io.shiftleft.codepropertygraph.generated.Cpg
import org.slf4j.LoggerFactory
import upickle.default.*
import scala.util.{Failure, Success, Try}
import java.net.URL

/** A process that runs a benchmark.
  */
trait BenchmarkRunner(datasetDir: File) {

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
    * @param expectedOutcome
    *   the expected outcome for the test.
    */
  protected def compare(testName: String, expectedOutcome: Boolean)(implicit cpg: Cpg): TestOutcome.Value

  /** The main benchmark runner entrypoint
    */
  def run(): Result

}

/** The result of a benchmark.
  */
case class Result(entries: List[TestEntry] = Nil) derives ReadWriter

/** A test and it's outcome.
  */
case class TestEntry(testName: String, outcome: TestOutcome.Value) derives ReadWriter

implicit val testOutcomeRw: ReadWriter[TestOutcome.Value] =
  readwriter[ujson.Value].bimap[TestOutcome.Value](x => ujson.Str(x.toString), json => TestOutcome.withName(json.str))

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
