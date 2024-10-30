package io.joern.benchmarks.runner

import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{BaseResult, PerformanceTestResult}

import scala.util.*

trait PerformanceTestRunner(k: Int) { this: BenchmarkRunner =>

  protected val packageNames: List[String]
  private var time: Long = 0L

  def getTimeSeconds: Double = time / 1_000_000_000.0

  /** Records the wall clock time taken for the given function to execute.
    */
  override def recordTime[T](f: () => T): T = {
    val start  = System.nanoTime()
    val result = f()
    time = System.nanoTime() - start
    result
  }

  override def run(iterations: Int): PerformanceTestResult = {
    var finalResult = PerformanceTestResult(k = k)
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        finalResult
      case Success(benchmarkDir) =>
        for {
          _ <- 0 until iterations
        } {
          finalResult = finalResult ++ runIteration.asInstanceOf[PerformanceTestResult]
        }
        finalResult
    }
  }

}
