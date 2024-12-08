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

class SecuribenchMicroJsJoernRunner(datasetDir: File, cpgCreator: JavaScriptCpgCreator[?], wholeProgram: Boolean)
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
    val inputDir = benchmarkBaseDir / subDirName
    if wholeProgram then setupWholeProgram(inputDir)
    try {
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
    } finally {
      if wholeProgram then cleanupWholeProgram(inputDir)
    }
  })

  private class SecuribenchMicroJsSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {
    override def sources: Iterator[CfgNode] = {
      cpg.assignment.where(_.source.fieldAccess.argument(1).isIdentifier.nameExact("req")).target
    }

    override def sinks: Iterator[CfgNode] = {
      val resCalls = cpg.call
        .nameExact("send", "write", "redirect")
        .where(_.receiver.fieldAccess.argument(1).isIdentifier.name("resp?", "writer"))
      val fsCalls = cpg.call
        .nameExact("createReadStream", "writeFileSync", "createWriteStream", "open")
        .where(_.receiver.fieldAccess.argument(1).isIdentifier.nameExact("fs"))
      val dbCalls = cpg.call.nameExact("query").where(_.receiver.fieldAccess.argument(1).isIdentifier.nameExact("db"))

      (resCalls ++ fsCalls ++ dbCalls).argument(1)
    }
  }

}
