package io.joern.benchmarks.runner

import better.files.File
import io.joern.benchmarks.Domain.*
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.semanticcpg.language.{ICallResolver, NoResolve}
import org.slf4j.{Logger, LoggerFactory}

import java.lang
import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.util.Try

/** A process that runs a benchmark.
  */
trait BenchmarkRunner(protected val datasetDir: File) {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  val baseDatasetsUrl: String   = "https://github.com/joernio/joern-benchmark-datasets/releases/download"
  val benchmarksVersion: String = "v0.10.0"

  val benchmarkName: String

  protected var currIter: Int      = 0
  private var times: Array[Double] = uninitialized

  def timeSeconds: List[Double] = {
    for { i <- times.indices } times(i) /= 1_000_000_000.0
    times.toList
  }

  /** Records the wall clock time taken for the given function to execute.
    */
  def recordTime[T](f: () => T): T = {
    val start  = System.nanoTime()
    val result = f()
    times(currIter) += System.nanoTime() - start
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
      case _ if flowExists   => TestOutcome.TP
      case _                 => TestOutcome.FP
    }
  }

  /** The main benchmark runner entrypoint
    */
  def run(iterations: Int): Result = {
    times = Array.ofDim(iterations)
    var result: Option[Result] = None
    for {
      _ <- 0 until iterations
    } {
      val iterResult = runIteration
      if result.isEmpty then result = Some(iterResult)
      currIter += 1
    }
    result.getOrElse(Result()).copy(times = timeSeconds)
  }

  protected def runIteration: Result

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
