package io.shiftleft.codepropertygraph.generated.traversal

import overflowdb.traversal._
import io.shiftleft.codepropertygraph.generated.nodes._

/** Traversal steps for SourceNode */
class SourceNodeTraversalExtGen[NodeType <: SourceNode](val traversal: Iterator[NodeType]) extends AnyVal {

  /** Traverse to CFG_NODE via MATCHES OUT edge.
    */
  def matchedNodes: Iterator[CfgNode] =
    traversal.flatMap(_.matchedNodes)

}
