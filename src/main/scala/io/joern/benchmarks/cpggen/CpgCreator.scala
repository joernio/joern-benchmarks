package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.benchmarks.runner.{BenchmarkSourcesAndSinks, DefaultBenchmarkSourcesAndSinks}
import io.joern.dataflowengineoss.DefaultSemantics
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.dataflowengineoss.semanticsloader.{FlowSemantic, Semantics}
import io.shiftleft.codepropertygraph.generated.Cpg

import scala.util.Try

trait CpgCreator {

  val frontend: String

  protected implicit val semantics: Semantics = Semantics.fromList(DefaultSemantics.operatorFlows ++ extraSemantics)
  protected implicit val engineContext: EngineContext = EngineContext()

  protected def extraSemantics: List[FlowSemantic] = Nil

  protected def F: (String, List[(Int, Int)]) => FlowSemantic = (x: String, y: List[(Int, Int)]) =>
    FlowSemantic.from(x, y)

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg]

}
