package io.joern.benchmarks.runner

import io.joern.benchmarks.Domain.{BaseResult, TaintAnalysisResult, TestOutcome}

import scala.compiletime.uninitialized

trait TaintAnalysisRunner { this: BenchmarkRunner =>

  protected var currIter: Int      = 0
  private var times: Array[Double] = uninitialized

  private def timeSeconds: List[Double] = {
    for { i <- times.indices } times(i) /= 1_000_000_000.0
    times.toList
  }

  /** Records the wall clock time taken for the given function to execute.
    */
  override def recordTime[T](f: () => T): T = {
    val start  = System.nanoTime()
    val result = f()
    times(currIter) += System.nanoTime() - start
    result
  }

  /** The findings for the given test.
    *
    * @return
    *   a list of findings, if any.
    */
  protected def findings(testName: String): List[FindingInfo]

  /** Compares the test expectations against the actual findings.
    *
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
  override def run(iterations: Int): BaseResult = {
    times = Array.ofDim(iterations)
    var result: Option[TaintAnalysisResult] = None
    for {
      _ <- 0 until iterations
    } {
      val iterResult = runIteration.asInstanceOf[TaintAnalysisResult]
      if result.isEmpty then result = Some(iterResult)
      currIter += 1
    }
    result.getOrElse(TaintAnalysisResult()).copy(times = timeSeconds)
  }
}

case class FindingInfo()
