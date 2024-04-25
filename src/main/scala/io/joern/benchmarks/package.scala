package io.joern

import io.shiftleft.codepropertygraph.generated.{Cpg, NodeTypes}
import io.shiftleft.codepropertygraph.generated.nodes.{Method, Finding, SourceNode, SinkNode}
import io.shiftleft.semanticcpg.language.*
import overflowdb.traversal.*
import overflowdb.traversal.help.{Doc, DocSearchPackages, Traversal, TraversalSource}

import scala.jdk.CollectionConverters.IteratorHasAsScala

package object benchmarks {

  // provides package names to search for @Doc annotations etc
  implicit val docSearchPackages: DocSearchPackages =
    () => io.shiftleft.codepropertygraph.Cpg.docSearchPackages() :+ this.getClass.getPackageName

  implicit def toBenchmarkStarts(cpg: Cpg): BenchmarkStarters =
    new BenchmarkStarters(cpg)

  /** Example of custom node type starters */
  @TraversalSource
  class BenchmarkStarters(cpg: Cpg) {
    def findings: Iterator[Finding] =
      cpg.graph.nodes(NodeTypes.FINDING).asScala.cast[Finding]

    def sources: Iterator[SourceNode] =
      cpg.graph.nodes(NodeTypes.SOURCE_NODE).asScala.cast[SourceNode]

    def sinks: Iterator[SinkNode] =
      cpg.graph.nodes(NodeTypes.SINK_NODE).asScala.cast[SinkNode]

  }
}
