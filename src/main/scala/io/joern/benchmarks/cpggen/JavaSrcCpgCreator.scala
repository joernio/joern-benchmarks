package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.dataflowengineoss.DefaultSemantics
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.dataflowengineoss.semanticsloader.{FlowSemantic, Semantics}
import io.shiftleft.codepropertygraph.generated.Cpg

import scala.util.Try
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.benchmarks.passes.{JavaTaggingPass, FindingsPass}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

trait JavaSrcCpgCreator extends CpgCreator {

  override def extraSemantics: List[FlowSemantic] = DefaultSemantics.javaFlows ++ List(
    F(
      "javax.servlet.http.HttpServletRequest.getParameter:<unresolvedSignature>(1)",
      (0, 0) :: (1, 1) :: (1, -1) :: (0, -1) :: Nil
    ),
    F("java.io.PrintWriter.println:void(java.lang.Object)", (0, 0) :: (1, 1) :: Nil),
    F("java.util.LinkedList.addLast:void(java.lang.Object)", (0, 0) :: (1, 1) :: (1, 0) :: Nil),
    F("java.util.LinkedList.add:void(java.lang.Object)", (0, 0) :: (1, 1) :: (1, 0) :: Nil),
    F("java.util.LinkedList.addAll:void(java.lang.Object)", (0, 0) :: (1, 1) :: (1, 0) :: Nil),
    F("java.util.ArrayList.addLast:void(java.lang.Object)", (0, 0) :: (1, 1) :: (1, 0) :: Nil),
    F("java.util.ArrayList.add:void(java.lang.Object)", (0, 0) :: (1, 1) :: (1, 0) :: Nil),
    F("java.util.ArrayList.addAll:void(java.lang.Object)", (0, 0) :: (1, 1) :: (1, 0) :: Nil),
    F(
      "java.util.Map.put:java.lang.Object(java.lang.Object,java.lang.Object)",
      (0, 0) :: (1, 1) :: (2, 2) :: (1, 0) :: (2, 0) :: (1, -1) :: (2, -1) :: Nil
    ),
    F("java.util.Map.get:java.lang.Object(java.lang.Object)", (0, 0) :: (1, 1) :: (1, -1) :: (0, -1) :: Nil),
    F("java.util.ArrayList.get:java.lang.Object(int)", (0, 0) :: (1, 1) :: (1, -1) :: (0, -1) :: Nil)
  )

  protected def createCpg(inputDir: File): Try[Cpg] = {
    val config = Config().withInputPath(inputDir.pathAsString)
    JavaSrc2Cpg().createCpgWithOverlays(config).map { cpg =>
      new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))
      new JavaTaggingPass(cpg).createAndApply()
      new FindingsPass(cpg).createAndApply()
      cpg
    }
  }

}
