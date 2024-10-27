package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.benchmarks.passes.{FindingsPass, JavaScriptTaggingPass}
import io.joern.benchmarks.runner.{BenchmarkSourcesAndSinks, DefaultBenchmarkSourcesAndSinks}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.semanticsloader.{FlowMapping, FlowSemantic}
import io.joern.jssrc2cpg.{JsSrc2Cpg, Config as JsSrcConfig}
import io.joern.x2cpg.X2CpgFrontend
import io.joern.x2cpg.frontendspecific.jssrc2cpg
import io.joern.x2cpg.passes.frontend.XTypeRecoveryConfig
import io.shiftleft.codepropertygraph.generated.{Cpg, Languages, Operators}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

import scala.util.Try

sealed trait JavaScriptCpgCreator[Frontend <: X2CpgFrontend[?]] extends CpgCreator {

  override def extraSemantics: List[FlowSemantic] = List(
    FlowSemantic("__ecma.String:trim", FlowMapping(0, 0) :: FlowMapping(0, -1) :: Nil),
    FlowSemantic(Operators.logicalNot, FlowMapping(1, -1) :: FlowMapping(2, -1) :: Nil),
    FlowSemantic(Operators.logicalAnd, FlowMapping(1, -1) :: FlowMapping(2, -1) :: Nil)
  )

  protected def runJavaScriptOverlays(
    cpg: Cpg,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Cpg = {
    new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))
    jssrc2cpg.postProcessingPasses(cpg, XTypeRecoveryConfig()).foreach(_.createAndApply())
    new JavaScriptTaggingPass(cpg, sourcesAndSinks(cpg)).createAndApply()
    new FindingsPass(cpg).createAndApply()
    cpg
  }

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg]

}

class JsSrcCpgCreator(override val disableSemantics: Boolean, override val maxCallDepth: Int)
    extends JavaScriptCpgCreator[JsSrc2Cpg] {

  override val frontend: String = Languages.JSSRC

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg] = {
    val config = JsSrcConfig().withInputPath(inputDir.pathAsString)
    JsSrc2Cpg().createCpgWithOverlays(config).map(runJavaScriptOverlays(_, sourcesAndSinks))
  }

}
