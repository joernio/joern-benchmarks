package io.joern.benchmarks.passes

import io.shiftleft.passes.CpgPass
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
      val lineNoPair = NewKeyValuePair()
        .key(LineNo)
        .value(sink.lineNumber.getOrElse(-1).toString) :: Nil
      val surroundingTypePair = sink.inAst.isMethod.typeDecl.name.map { typeName =>
        NewKeyValuePair()
          .key(SurroundingType)
          .value(typeName)
      }.toSeq
      val fileNamePair = sink.inAst.isMethod.file.name.map(NewKeyValuePair().key(FileName).value(_)).toSeq

      val pairs   = lineNoPair ++ surroundingTypePair ++ fileNamePair
      val finding = NewFinding().evidence(elements).keyValuePairs(pairs)
      builder.addNode(finding)
    }
  }

}

object FindingsPass {
  val FileName        = "FILE_NAME"
  val SurroundingType = "SURROUNDING_TYPE"
  val LineNo          = "LINE_NO"
}
