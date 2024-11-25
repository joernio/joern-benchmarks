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
  val benchmarksVersion: String = "v0.13.0"

  val benchmarkName: String

  /** Records the wall clock time taken for the given function to execute.
    */
  def recordTime[T](f: () => T): T

  /** Create and setup the benchmark if necessary.
    *
    * @return
    *   the directory where the benchmark is set up if successful.
    */
  protected def initialize(): Try[File]

  def run(iterations: Int): BaseResult

  protected def runIteration: BaseResult

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
