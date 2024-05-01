package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.benchmarks.passes.{FindingsPass, JavaScriptTaggingPass, JavaTaggingPass}
import io.joern.benchmarks.runner.{BenchmarkSourcesAndSinks, DefaultBenchmarkSourcesAndSinks}
import io.joern.dataflowengineoss.DefaultSemantics
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.semanticsloader.FlowSemantic
import io.joern.jssrc2cpg.{JsSrc2Cpg, Config as JsSrcConfig}
import io.joern.x2cpg.X2CpgFrontend
import io.shiftleft.codepropertygraph.generated.{Cpg, Languages}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

import scala.util.Try

sealed trait JavaScriptCpgCreator[Frontend <: X2CpgFrontend[?]] extends CpgCreator {

  protected def runJavaScriptOverlays(
    cpg: Cpg,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Cpg = {
    new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))
    JsSrc2Cpg.postProcessingPasses(cpg).foreach(_.createAndApply())
    new JavaScriptTaggingPass(cpg, sourcesAndSinks(cpg)).createAndApply()
    new FindingsPass(cpg).createAndApply()
    cpg
  }

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg]

}

class JsSrcCpgCreator extends JavaScriptCpgCreator[JsSrc2Cpg] {

  override val frontend: String = Languages.JSSRC

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg] = {
    val config = JsSrcConfig().withInputPath(inputDir.pathAsString)
    JsSrc2Cpg().createCpgWithOverlays(config).map(runJavaScriptOverlays(_, sourcesAndSinks))
  }

}
