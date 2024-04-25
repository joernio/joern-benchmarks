package io.joern.benchmarks.passes

import io.shiftleft.passes.CpgPass
import overflowdb.BatchedUpdate
import io.shiftleft.codepropertygraph.generated.{Cpg, EdgeTypes}
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, NewSourceNode, NewSinkNode}

class JavaTaggingPass(cpg: Cpg)(implicit resolver: ICallResolver = NoResolve) extends CpgPass(cpg) {

  override def run(builder: BatchedUpdate.DiffGraphBuilder): Unit = {
    sources.foreach(builder.addEdge(NewSourceNode(), _, EdgeTypes.MATCHES))
    sinks.foreach(builder.addEdge(NewSinkNode(), _, EdgeTypes.MATCHES))
  }

  private def sources: Iterator[CfgNode] = {
    cpg.typeDecl
      .fullNameExact("javax.servlet.http.HttpServletRequest")
      .referencingType
      .flatMap(_.evalTypeIn)
      .isParameter ++
      cpg.typeDecl
        .fullNameExact("javax.servlet.http.Cookie")
        .method
        .nameExact("getValue")
        .methodReturn

  }

  private def sinks: Iterator[CfgNode] = {
    cpg.method.fullName("java\\.io\\.File(?:Writer)?\\.(?:<init>|write).*").parameter.argument
  }

}
