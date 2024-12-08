package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JVMBytecodeCpgCreator, JavaCpgCreator, JavaSrcCpgCreator}
import io.joern.benchmarks.passes.FindingsPass
import io.joern.benchmarks.runner.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Finding}
import io.shiftleft.semanticcpg.language.*

import scala.util.{Failure, Success, Using}

class SecuribenchMicroJoernRunner(datasetDir: File, cpgCreator: JavaCpgCreator[?], wholeProgram: Boolean)
    extends SecuribenchMicroRunner(datasetDir, cpgCreator.frontend)
    with CpgBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cpg.findings
      .filter(_.keyValuePairs.keyExact(FindingsPass.SurroundingType).exists(_.value == name))
      .filter(_.keyValuePairs.keyExact(FindingsPass.LineNo).exists(_.value == lineNo))
      .map(mapToFindingInfo)
      .l
  }

  override def runIteration: BaseResult = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        TaintAnalysisResult()
      case Success(benchmarkDir) =>
        runSecuribenchMicro()
    }
  }

  private def runSecuribenchMicro(): BaseResult = recordTime(() => {
    val inputDir = cpgCreator match {
      case _: JVMBytecodeCpgCreator => benchmarkBaseDir / "classes"
      case _: JavaSrcCpgCreator     => benchmarkBaseDir / "src"
    }
    if wholeProgram then {
      (benchmarkBaseDir / "lib").list.foreach(l => l.copyToDirectory(inputDir))
    }
    try {
      cpgCreator.createCpg(inputDir, cpg => SecuribenchMicroSourcesAndSinks(cpg)) match {
        case Failure(exception) =>
          logger.error(s"Unable to generate CPG for $benchmarkName", exception)
          TaintAnalysisResult()
        case Success(cpg) =>
          Using.resource(cpg) { cpg =>
            setCpg(cpg)
            val expectedTestOutcomes = getExpectedTestOutcomes
            val testResults = expectedTestOutcomes
              .map { case (testName, outcome) =>
                TestEntry(testName, compare(testName, outcome))
              }
              .sortBy(_.testName)
              .l
            TaintAnalysisResult(testResults)
          }
      }
    } finally {
      if wholeProgram then {
        (benchmarkBaseDir / "lib").list.foreach(l => (inputDir / l.name).delete(true))
      }
    }
  })

  class SecuribenchMicroSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {
    override def sources: Iterator[CfgNode] =
      cpg.parameter.and(_.index(1), _.method.name("foo"), _.typeFullNameExact("java.lang.Object"))
  }

}
