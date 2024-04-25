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

  /** Example of a custom language step
    */
//  implicit class MynodetypeSteps(val traversal: Iterator[Mynodetype]) extends AnyVal {
//    def myCustomStep: Iterator[Mynodetype] = {
//      println("custom step executed")
//      traversal
//    }
//  }
//
//  @Traversal(elementType = classOf[Method])
//  implicit class CustomMethodSteps(val traversal: Iterator[Method]) extends AnyVal {
//    @Doc("custom step on method as an example", "a veeery long description again")
//    def customMethodStep: Iterator[String] =
//      traversal.flatMap(_.parameter.name)
//  }
//
//  /** Example implicit conversion that forwards to the `StandaloneStarters` class
//    */
//  implicit def toStandaloneStarters(cpg: Cpg): StandaloneStarters =
//    new StandaloneStarters(cpg)
//
//  /** Example of custom node type starters */
//  @TraversalSource
//  class StandaloneStarters(cpg: Cpg) {
//    def findings: Iterator[Finding] =
//      cpg.graph.nodes(NodeTypes.FINDING).asScala.cast[Finding]
//
//    @Doc("custom starter step as an example", "a veeery long description")
//    def customStarterStep: Iterator[String] =
//      cpg.method.parameter.name
//  }
}
