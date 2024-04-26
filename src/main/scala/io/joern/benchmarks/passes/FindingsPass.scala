package io.joern.benchmarks.passes

import io.shiftleft.passes.CpgPass
import overflowdb.BatchedUpdate
import io.shiftleft.semanticcpg.language.*
import io.joern.benchmarks.*
import io.joern.dataflowengineoss.language.*
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.shiftleft.codepropertygraph.generated.{Cpg, EdgeTypes}
import io.shiftleft.codepropertygraph.generated.nodes.{
  CfgNode,
  Method,
  NewFinding,
  NewKeyValuePair,
  NewSinkNode,
  SourceNode
}

class FindingsPass(cpg: Cpg)(implicit val context: EngineContext) extends CpgPass(cpg) {

  override def run(builder: DiffGraphBuilder): Unit = {
    val sources = cpg.sources._matchesOut.cast[CfgNode]
    val sinks   = cpg.sinks._matchesOut.cast[CfgNode]
    sinks.reachableByFlows(sources).foreach { case Path(elements) =>
      val sink = elements.last
      val kvPairs = sink.inAst.collectAll[Method].typeDecl.name.map { testName =>
        NewKeyValuePair()
          .key("TEST_NAME")
          .value(s"$testName:${sink.lineNumber.getOrElse(-1)}")
      }
      val finding = NewFinding().evidence(elements).keyValuePairs(kvPairs)
      builder.addNode(finding)
    }
  }

}
