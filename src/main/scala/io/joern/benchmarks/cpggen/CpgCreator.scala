package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.benchmarks.runner.{BenchmarkSourcesAndSinks, DefaultBenchmarkSourcesAndSinks}
import io.joern.dataflowengineoss.DefaultSemantics
import io.joern.dataflowengineoss.queryengine.{EngineConfig, EngineContext}
import io.joern.dataflowengineoss.semanticsloader.{FlowSemantic, FullNameSemantics, Semantics}
import io.shiftleft.codepropertygraph.generated.Cpg

import scala.util.Try

trait CpgCreator {

  val frontend: String
  protected val disableSemantics: Boolean

  protected implicit lazy val semantics: Semantics =
    if disableSemantics then FullNameSemantics.fromList(Nil)
    else FullNameSemantics.fromList(DefaultSemantics.operatorFlows ++ extraSemantics)
  protected implicit lazy val engineContext: EngineContext = EngineContext(semantics)

  protected def extraSemantics: List[FlowSemantic] = Nil

  protected def F: (String, List[(Int, Int)]) => FlowSemantic = (x: String, y: List[(Int, Int)]) =>
    FlowSemantic.from(x, y)

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg]

}
