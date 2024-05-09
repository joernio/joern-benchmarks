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

  import FindingsPass.*

  override def run(builder: DiffGraphBuilder): Unit = {
    cpg.sinks.reachableByFlows(cpg.sources).passesNot(_.isSanitizer).foreach { case Path(elements) =>
      val sink = elements.last
      val kvPairs = NewKeyValuePair()
        .key(LineNo)
        .value(sink.lineNumber.getOrElse(-1).toString()) +: sink.inAst
        .collectAll[Method]
        .typeDecl
        .name
        .map { typeName =>
          NewKeyValuePair()
            .key(SurroundingType)
            .value(typeName)
        }
        .toSeq
      val finding = NewFinding().evidence(elements).keyValuePairs(kvPairs)
      builder.addNode(finding)
    }
  }

}

object FindingsPass {
  val SurroundingType = "SURROUNDING_TYPE"
  val LineNo          = "LINE_NO"
}
