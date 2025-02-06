package io.joern

import flatgraph.DiffGraphBuilder
import io.shiftleft.codepropertygraph.generated.{Cpg, NodeTypes}
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*
import flatgraph.help.{Doc, DocSearchPackages, Traversal, TraversalSource}

import scala.jdk.CollectionConverters.*

package object benchmarks {

  // provides package names to search for @Doc annotations etc
  implicit val docSearchPackages: DocSearchPackages =
    Cpg.defaultDocSearchPackage
      .withAdditionalPackage(this.getClass.getPackageName)

  implicit def toBenchmarkStarters(cpg: Cpg): BenchmarkStarters =
    new BenchmarkStarters(cpg)

  implicit def toCfgTaggingExt[T <: StoredNode](traversal: Iterator[T]): TaggingExt[T] =
    new TaggingExt(traversal)

  @TraversalSource
  class BenchmarkStarters(cpg: Cpg) {

    def findings: Iterator[Finding] =
      cpg.graph.nodes(NodeTypes.FINDING).cast[Finding]

    def sources: Iterator[CfgNode] =
      cpg.all.where(_.tag.name("SOURCE")).collectAll[CfgNode]

    def sinks: Iterator[CfgNode] =
      cpg.all.where(_.tag.name("SINK")).collectAll[CfgNode]

    def sanitizers: Iterator[CfgNode] =
      cpg.all.where(_.tag.name("SANITIZER")).collectAll[CfgNode]
  }

  class TaggingExt[T <: StoredNode](traversal: Iterator[T]) {

    def tagAsSource(implicit diffGraph: DiffGraphBuilder): Unit = traversal.newTagNode("SOURCE").store()

    def tagAsSink(implicit diffGraph: DiffGraphBuilder): Unit = traversal.newTagNode("SINK").store()

    def tagAsSanitizer(implicit diffGraph: DiffGraphBuilder): Unit = traversal.newTagNode("SANITIZER").store()

    def isSanitizer: Iterator[T] = traversal.where(_.tag.nameExact("SANITIZER"))

  }
}
