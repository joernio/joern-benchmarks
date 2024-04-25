package io.shiftleft.codepropertygraph.generated.traversal

import overflowdb.traversal._
import io.shiftleft.codepropertygraph.generated.nodes._

/** Traversal steps for SinkNode */
class SinkNodeTraversalExtGen[NodeType <: SinkNode](val traversal: Iterator[NodeType]) extends AnyVal {

  /** Traverse to CFG_NODE via MATCHES OUT edge.
    */
  def matchedNodes: Iterator[CfgNode] =
    traversal.flatMap(_.matchedNodes)

}
