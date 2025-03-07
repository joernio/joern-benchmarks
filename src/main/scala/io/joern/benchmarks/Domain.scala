package io.joern.benchmarks

import upickle.default.*

import scala.annotation.targetName

object Domain {

  sealed trait BaseResult

  private def mean[B](arr: Seq[B])(implicit num: Numeric[B]): Double = {
    if (arr.nonEmpty) {
      num.toDouble(arr.sum) / arr.size
    } else {
      0.0
    }
  }

  private def stderr[B](arr: Seq[B], mean: Double)(implicit num: Numeric[B]): Double = {
    val iterations = arr.size
    if (arr.nonEmpty) {
      val variance          = arr.map(t => math.pow(num.toDouble(t) - mean, 2)).sum / (iterations - 1)
      val standardDeviation = math.sqrt(variance)
      standardDeviation / math.sqrt(iterations)
    } else {
      0.0
    }
  }

  implicit val resultRw: ReadWriter[TaintAnalysisResult] =
    readwriter[ujson.Value].bimap[TaintAnalysisResult](
      x =>
        ujson.Obj(
          "entries"       -> write[List[TestEntry]](x.entries),
          "informedness"  -> x.jIndex,
          "truePositive"  -> x.tp,
          "falsePositive" -> x.fp,
          "trueNegative"  -> x.tn,
          "falseNegative" -> x.fp
        ),
      json => TaintAnalysisResult(read[List[TestEntry]](json("entries")))
    )

  case class PerformanceTestResult(entries: List[PerfRun] = Nil, k: Int) extends BaseResult derives ReadWriter {
    @targetName("appendAll")
    def ++(o: PerformanceTestResult): PerformanceTestResult = {
      copy(entries ++ o.entries)
    }
  }

  case class PerfRun(name: String, time: Double, memory: List[Long]) derives ReadWriter {
    def meanMem: Double = mean(memory)

    def stderrMem: Double = stderr(memory, meanMem)
  }

  /** The result of a benchmark.
    */
  case class TaintAnalysisResult(entries: List[TestEntry] = Nil, times: List[Double] = Nil, memory: List[Long] = Nil)
      extends BaseResult {

    /** @return
      *   When a benchmark tests for false/true positives/negatives, this will be the <a
      *   href="https://en.wikipedia.org/wiki/Youden%27s_J_statistic">Youden's J index</a>. If only two dimensions are
      *   tested, it will simply be a measure of the precision of these two (either sensitivity or specificity).
      */
    def jIndex: Double = {
      if (tn == 0 && fp == 0) tp / (tp + fn)
      else if (tp == 0 && fn == 0) tn / (tn + fp)
      else tp / (tp + fn) + tn / (tn + fp) - 1.0
    }

    /** @return
      *   When a benchmark tests for false/true positives/negatives, this will be the <a
      *   href="https://en.wikipedia.org/wiki/F-score">F1 score</a>. If only two dimensions are tested, it will simply
      *   be a measure of the precision of these two (either sensitivity or specificity).
      */
    def fscore: Double = {
      if (tn == 0 && fp == 0) tp / (tp + fn)
      else if (tp == 0 && fn == 0) tn / (tn + fp)
      else 2 * tp / (2 * tp + fp + fn)
    }

    def tp: Double = entries.count(_.outcome == TestOutcome.TP).toDouble
    def fp: Double = entries.count(_.outcome == TestOutcome.FP).toDouble
    def tn: Double = entries.count(_.outcome == TestOutcome.TN).toDouble
    def fn: Double = entries.count(_.outcome == TestOutcome.FN).toDouble

    def meanTime: Double = mean(times)

    def stderrTime: Double = stderr(times, meanTime)

    def meanMem: Double = mean(memory)

    def stderrMem: Double = stderr(memory, meanMem)

    @targetName("appendAll")
    def ++(o: TaintAnalysisResult): TaintAnalysisResult = {
      copy(entries ++ o.entries)
    }

  }

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
}
