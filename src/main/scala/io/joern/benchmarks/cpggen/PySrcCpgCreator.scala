package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.benchmarks.passes.{FindingsPass, PythonTaggingPass}
import io.joern.benchmarks.runner.{BenchmarkSourcesAndSinks, DefaultBenchmarkSourcesAndSinks}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.semanticsloader.{FlowMapping, FlowSemantic}
import io.joern.pysrc2cpg.{
  DynamicTypeHintFullNamePass,
  ImportsPass,
  PythonImportResolverPass,
  PythonInheritanceNamePass,
  PythonTypeHintCallLinker,
  PythonTypeRecoveryPassGenerator,
  Py2CpgOnFileSystem as PySrc2Cpg,
  Py2CpgOnFileSystemConfig as PySrcConfig
}
import io.joern.x2cpg.X2CpgFrontend
import io.joern.x2cpg.passes.base.AstLinkerPass
import io.joern.x2cpg.passes.callgraph.NaiveCallLinker
import io.joern.x2cpg.passes.frontend.XTypeRecoveryConfig
import io.shiftleft.codepropertygraph.generated.{Cpg, Languages}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

import scala.util.Try

sealed trait PythonCpgCreator[Frontend <: X2CpgFrontend[?]] extends CpgCreator {

  override def extraSemantics: List[FlowSemantic] = List(
    FlowSemantic("collections.py.*\\.popleft", FlowMapping(0, -1) :: FlowMapping(0, 0) :: Nil, true),
    FlowSemantic("__builtin\\.dict.*\\.get", FlowMapping(0, -1) :: FlowMapping(0, 0) :: Nil, true),
    FlowSemantic("__builtin\\.dict.*\\.keys", FlowMapping(0, -1) :: FlowMapping(0, 0) :: Nil, true),
    FlowSemantic(
      "__builtin\\.list.*\\.append",
      FlowMapping(1, 0) :: FlowMapping(1, 1) :: FlowMapping(0, 0) :: Nil,
      true
    )
  )

  protected def runPythonOverlays(
    cpg: Cpg,
    pyConfig: PySrcConfig,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Cpg = {
    new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))
    new ImportsPass(cpg).createAndApply()
    new PythonImportResolverPass(cpg).createAndApply()
    new DynamicTypeHintFullNamePass(cpg).createAndApply()
    new PythonInheritanceNamePass(cpg).createAndApply()
    val typeRecoveryConfig = XTypeRecoveryConfig(pyConfig.typePropagationIterations, !pyConfig.disableDummyTypes)
    new PythonTypeRecoveryPassGenerator(cpg, typeRecoveryConfig).generate().foreach(_.createAndApply())
    new PythonTypeHintCallLinker(cpg).createAndApply()
    new NaiveCallLinker(cpg).createAndApply()

    // Some of passes above create new methods, so, we
    // need to run the ASTLinkerPass one more time
    new AstLinkerPass(cpg).createAndApply()
    new PythonTaggingPass(cpg, sourcesAndSinks(cpg)).createAndApply()
    new FindingsPass(cpg).createAndApply()
    cpg
  }

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg]

}

class PySrcCpgCreator(override val disableSemantics: Boolean) extends PythonCpgCreator[PySrc2Cpg] {

  override val frontend: String = Languages.PYTHONSRC

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg] = {
    val config = PySrcConfig().withInputPath(inputDir.pathAsString)
    PySrc2Cpg().createCpgWithOverlays(config).map(runPythonOverlays(_, config, sourcesAndSinks))
  }

}
