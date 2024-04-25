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

trait JavaSrcCpgCreator {

  protected implicit val semantics: Semantics = Semantics.fromList(DefaultSemantics.operatorFlows ++ extraSemantics)
  protected implicit val engineContext: EngineContext = EngineContext()

  protected def extraSemantics: List[FlowSemantic] = DefaultSemantics.javaFlows

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
