package io.joern.benchmarks

import upickle.default.*

object Domain {

  implicit val resultRw: ReadWriter[Result] =
    readwriter[ujson.Value].bimap[Result](
      x => ujson.Obj("entries" -> write[List[TestEntry]](x.entries), "youdenIndex" -> x.youdenIndex),
      json => Result(read[List[TestEntry]](json("entries")))
    )

  /** The result of a benchmark.
    */
  case class Result(entries: List[TestEntry] = Nil) {

    def youdenIndex: Double = {
      val tp = entries.count(_.outcome == TestOutcome.TP).toDouble
      val fp = entries.count(_.outcome == TestOutcome.FP).toDouble
      val tn = entries.count(_.outcome == TestOutcome.TN).toDouble
      val fn = entries.count(_.outcome == TestOutcome.FN).toDouble
      tp / (tp + fn) + tn / (tn + fp) - 1.0
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
