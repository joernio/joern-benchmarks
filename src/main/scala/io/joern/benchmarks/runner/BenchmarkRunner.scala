package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.Domain.*
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.semanticcpg.language.{ICallResolver, NoResolve}
import org.slf4j.{Logger, LoggerFactory}

import java.lang
import scala.util.Try

/** A process that runs a benchmark.
  */
trait BenchmarkRunner(protected val datasetDir: File) {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  val baseDatasetsUrl: String   = "https://github.com/joernio/joern-benchmark-datasets/releases/download"
  val benchmarksVersion: String = "ASYDE-2024"

  val benchmarkName: String

  private var totalTime = 0L

  def timeSeconds: Double = {
    totalTime / 1_000_000_000.0;
  }

  /** Records the wall clock time taken for the given function to execute.
    */
  def recordTime[T](f: () => T): T = {
    val start  = System.nanoTime()
    val result = f()
    totalTime += System.nanoTime() - start
    result
  }

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
  protected def findings(testName: String): List[FindingInfo]

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

case class FindingInfo()
