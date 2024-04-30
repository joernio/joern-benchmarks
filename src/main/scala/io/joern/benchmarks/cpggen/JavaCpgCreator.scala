package io.joern.benchmarks.cpggen

import better.files.File
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.benchmarks.runner.{BenchmarkSourcesAndSinks, DefaultBenchmarkSourcesAndSinks}
import io.joern.dataflowengineoss.DefaultSemantics
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.semanticsloader.FlowSemantic
import io.joern.javasrc2cpg.{Config as JavaSrcConfig, JavaSrc2Cpg}
import io.joern.jimple2cpg.{Config as JimpleConfig, Jimple2Cpg}
import io.joern.x2cpg.X2CpgFrontend
import io.shiftleft.codepropertygraph.generated.{Cpg, Languages}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

import scala.util.Try

sealed trait JavaCpgCreator[Frontend <: X2CpgFrontend[?]] extends CpgCreator {

  val frontend: String

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

  protected def runJavaOverlays(
    cpg: Cpg,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Cpg = {
    new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))
    new JavaTaggingPass(cpg, sourcesAndSinks(cpg)).createAndApply()
    new FindingsPass(cpg).createAndApply()
    cpg
  }

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg]

}

class JavaSrcCpgCreator extends JavaCpgCreator[JavaSrc2Cpg] {

  override val frontend: String = Languages.JAVASRC

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg] = {
    val config = JavaSrcConfig().withInputPath(inputDir.pathAsString)
    JavaSrc2Cpg().createCpgWithOverlays(config).map(runJavaOverlays(_, sourcesAndSinks))
  }

}

class JVMBytecodeCpgCreator extends JavaCpgCreator[Jimple2Cpg] {

  override val frontend: String = Languages.JAVA

  def createCpg(
    inputDir: File,
    sourcesAndSinks: Cpg => BenchmarkSourcesAndSinks = { _ => DefaultBenchmarkSourcesAndSinks() }
  ): Try[Cpg] = {
    val config = JimpleConfig().withInputPath(inputDir.pathAsString).withIgnoredFilesRegex("^lib.*")
    Jimple2Cpg().createCpgWithOverlays(config).map(runJavaOverlays(_, sourcesAndSinks))
  }

}
