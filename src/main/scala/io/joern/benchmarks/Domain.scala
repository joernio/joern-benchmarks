package io.joern.benchmarks

import upickle.default.*

import scala.annotation.targetName

object Domain {

  implicit val resultRw: ReadWriter[Result] =
    readwriter[ujson.Value].bimap[Result](
      x =>
        ujson.Obj(
          "entries"       -> write[List[TestEntry]](x.entries),
          "informedness"  -> x.jIndex,
          "truePositive"  -> x.tp,
          "falsePositive" -> x.fp,
          "trueNegative"  -> x.tn,
          "falseNegative" -> x.fp
        ),
      json => Result(read[List[TestEntry]](json("entries")))
    )

  /** The result of a benchmark.
    */
  case class Result(entries: List[TestEntry] = Nil, time: Double = 0d) {

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

    @targetName("appendAll")
    def ++(o: Result): Result = {
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
