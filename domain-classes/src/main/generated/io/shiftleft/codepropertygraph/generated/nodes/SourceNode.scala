package io.shiftleft.codepropertygraph.generated.nodes

import overflowdb._
import scala.jdk.CollectionConverters._

object SourceNode {
  def apply(graph: Graph, id: Long) = new SourceNode(graph, id)

  val Label = "SOURCE_NODE"

  object PropertyNames {

    val all: Set[String]                 = Set()
    val allAsJava: java.util.Set[String] = all.asJava
  }

  object Properties {}

  object PropertyDefaults {}

  val layoutInformation = new NodeLayoutInformation(
    Label,
    PropertyNames.allAsJava,
    List(io.shiftleft.codepropertygraph.generated.edges.Matches.layoutInformation).asJava,
    List().asJava
  )

  object Edges {
    val Out: Array[String] = Array("MATCHES")
    val In: Array[String]  = Array()
  }

  val factory = new NodeFactory[SourceNodeDb] {
    override val forLabel = SourceNode.Label

    override def createNode(ref: NodeRef[SourceNodeDb]) =
      new SourceNodeDb(ref.asInstanceOf[NodeRef[NodeDb]])

    override def createNodeRef(graph: Graph, id: Long) = SourceNode(graph, id)
  }
}

trait SourceNodeBase extends AbstractNode {
  def asStored: StoredNode = this.asInstanceOf[StoredNode]

}

class SourceNode(graph_4762: Graph, id_4762: Long /*cf https://github.com/scala/bug/issues/4762 */ )
    extends NodeRef[SourceNodeDb](graph_4762, id_4762)
    with SourceNodeBase
    with StoredNode {

  override def propertyDefaultValue(propertyKey: String) = {
    propertyKey match {

      case _ => super.propertyDefaultValue(propertyKey)
    }
  }

  def matchesOut: Iterator[CfgNode] = get().matchesOut
  override def _matchesOut          = get()._matchesOut

  /** Traverse to CFG_NODE via MATCHES OUT edge.
    */
  def matchedNodes: overflowdb.traversal.Traversal[CfgNode] = get().matchedNodes

  // In view of https://github.com/scala/bug/issues/4762 it is advisable to use different variable names in
  // patterns like `class Base(x:Int)` and `class Derived(x:Int) extends Base(x)`.
  // This must become `class Derived(x_4762:Int) extends Base(x_4762)`.
  // Otherwise, it is very hard to figure out whether uses of the identifier `x` refer to the base class x
  // or the derived class x.
  // When using that pattern, the class parameter `x_47672` should only be used in the `extends Base(x_4762)`
  // clause and nowhere else. Otherwise, the compiler may well decide that this is not just a constructor
  // parameter but also a field of the class, and we end up with two `x` fields. At best, this wastes memory;
  // at worst both fields go out-of-sync for hard-to-debug correctness bugs.

  override def fromNewNode(newNode: NewNode, mapping: NewNode => StoredNode): Unit = get().fromNewNode(newNode, mapping)
  override def canEqual(that: Any): Boolean                                        = get.canEqual(that)
  override def label: String = {
    SourceNode.Label
  }

  override def productElementName(n: Int): String =
    n match {
      case 0 => "id"
    }

  override def productElement(n: Int): Any =
    n match {
      case 0 => id
    }

  override def productPrefix = "SourceNode"
  override def productArity  = 1
}

class SourceNodeDb(ref: NodeRef[NodeDb]) extends NodeDb(ref) with StoredNode with SourceNodeBase {

  override def layoutInformation: NodeLayoutInformation = SourceNode.layoutInformation

  /** faster than the default implementation */
  override def propertiesMap: java.util.Map[String, Any] = {
    val properties = new java.util.HashMap[String, Any]

    properties
  }

  /** faster than the default implementation */
  override def propertiesMapForStorage: java.util.Map[String, Any] = {
    val properties = new java.util.HashMap[String, Any]

    properties
  }

  import overflowdb.traversal._
  def matchesOut: Iterator[CfgNode]                         = createAdjacentNodeScalaIteratorByOffSet[CfgNode](0)
  override def _matchesOut                                  = createAdjacentNodeScalaIteratorByOffSet[StoredNode](0)
  def matchedNodes: overflowdb.traversal.Traversal[CfgNode] = matchesOut.collectAll[CfgNode]

  override def label: String = {
    SourceNode.Label
  }

  override def productElementName(n: Int): String =
    n match {
      case 0 => "id"
    }

  override def productElement(n: Int): Any =
    n match {
      case 0 => id
    }

  override def productPrefix = "SourceNode"
  override def productArity  = 1

  override def canEqual(that: Any): Boolean = that != null && that.isInstanceOf[SourceNodeDb]

  override def property(key: String): Any = {
    key match {

      case _ => null
    }
  }

  override protected def updateSpecificProperty(key: String, value: Object): Unit = {
    key match {

      case _ => PropertyErrorRegister.logPropertyErrorIfFirst(getClass, key)
    }
  }

  override def removeSpecificProperty(key: String): Unit =
    this.updateSpecificProperty(key, null)

  override def _initializeFromDetached(
    data: overflowdb.DetachedNodeData,
    mapper: java.util.function.Function[overflowdb.DetachedNodeData, Node]
  ) =
    fromNewNode(data.asInstanceOf[NewNode], nn => mapper.apply(nn).asInstanceOf[StoredNode])

  override def fromNewNode(newNode: NewNode, mapping: NewNode => StoredNode): Unit = {}

}
