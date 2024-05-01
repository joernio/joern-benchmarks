package io.joern.benchmarks.passes

import io.shiftleft.passes.CpgPass
import overflowdb.BatchedUpdate
import io.shiftleft.semanticcpg.language.*
import io.joern.benchmarks.*
import io.joern.dataflowengineoss.language.*
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.shiftleft.codepropertygraph.generated.{Cpg, EdgeTypes}
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Method, NewFinding, NewKeyValuePair}

class FindingsPass(cpg: Cpg)(implicit val context: EngineContext) extends CpgPass(cpg) {

  override def run(builder: DiffGraphBuilder): Unit = {
    cpg.sinks.reachableByFlows(cpg.sources).passesNot(_.isSanitizer).foreach { case Path(elements) =>
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
