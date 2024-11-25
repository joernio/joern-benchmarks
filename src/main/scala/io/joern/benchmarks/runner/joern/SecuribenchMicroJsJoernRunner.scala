package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.JavaScriptCpgCreator
import io.joern.benchmarks.passes.FindingsPass
import io.joern.benchmarks.runner.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{CfgNode, Finding}
import io.shiftleft.semanticcpg.language.*

import scala.util.{Failure, Success, Using}

class SecuribenchMicroJsJoernRunner(datasetDir: File, cpgCreator: JavaScriptCpgCreator[?])
    extends SecuribenchMicroJsRunner(datasetDir, cpgCreator.frontend)
    with CpgBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cpg.findings
      .filter(_.keyValuePairs.keyExact(FindingsPass.FileName).exists(f => fileNameToTestName(f.value) == name))
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
    val inputDir = benchmarkBaseDir / "securibench-micro.js-1.0.1" / "test-cases"
    cpgCreator.createCpg(inputDir, cpg => SecuribenchMicroJsSourcesAndSinks(cpg)) match {
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
  })

  private class SecuribenchMicroJsSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {
    override def sources: Iterator[CfgNode] =
      cpg.parameter.and(_.index(2), _.method.name("handler"))

    override def sinks: Iterator[CfgNode] = {
      val sinkCalls = cpg.call
        .nameExact("send", "write", "redirect")
        .where(_.receiver.fieldAccess.argument(1).isIdentifier.name("resp?", "writer")) ++
        cpg.call
          .nameExact("createReadStream", "writeFileSync", "createWriteStream", "open")
          .where(_.receiver.fieldAccess.argument(1).isIdentifier.nameExact("fs")) ++
        cpg.call.name("query", "execute.*").where(_.receiver.fieldAccess.argument(1).isIdentifier.nameExact("db"))

      sinkCalls.argument(1)
    }
  }

}
