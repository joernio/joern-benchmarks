package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.Domain.*
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Finding}
import io.shiftleft.semanticcpg.language.{ICallResolver, NoResolve}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

/** A process that runs a benchmark.
  */
trait BenchmarkRunner(protected val datasetDir: File) {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

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
  protected def findings(testName: String): List[Finding]

  /** Compares the test expectations against the actual findings.
    * @param testName
    *   the test name, from where the expected result can be retrieved.
    * @param flowExists
    *   the expected outcome for the test.
    */
  protected def compare(testName: String, flowExists: Boolean): TestOutcome.Value = {
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

/** Used to specify benchmark-specific sources and sinks.
  */
trait BenchmarkSourcesAndSinks {

  protected implicit val resolver: ICallResolver      = NoResolve
  protected implicit val engineContext: EngineContext = EngineContext()

  def sources: Iterator[CfgNode] = Iterator.empty

  def sinks: Iterator[CfgNode] = Iterator.empty

  def sanitizers: Iterator[CfgNode] = Iterator.empty

}

class DefaultBenchmarkSourcesAndSinks extends BenchmarkSourcesAndSinks
