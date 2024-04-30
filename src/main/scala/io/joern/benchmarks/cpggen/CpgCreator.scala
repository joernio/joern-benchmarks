package io.joern.benchmarks.cpggen

import io.joern.dataflowengineoss.DefaultSemantics
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.dataflowengineoss.semanticsloader.{FlowSemantic, Semantics}

trait CpgCreator {

  protected implicit val semantics: Semantics = Semantics.fromList(DefaultSemantics.operatorFlows ++ extraSemantics)
  protected implicit val engineContext: EngineContext = EngineContext()

  protected def extraSemantics: List[FlowSemantic] = Nil

  protected def F: (String, List[(Int, Int)]) => FlowSemantic = (x: String, y: List[(Int, Int)]) =>
    FlowSemantic.from(x, y)

}
