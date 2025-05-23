package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.PythonCpgCreator
import io.joern.benchmarks.passes.FindingsPass
import io.joern.benchmarks.runner.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{Call, CfgNode, Finding}
import io.shiftleft.semanticcpg.language.*

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Using}

class ThoratJoernRunner(datasetDir: File, cpgCreator: PythonCpgCreator[?], wholeProgram: Boolean)
    extends ThoratPythonRunner(datasetDir, cpgCreator.frontend)
    with CpgBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cpg.findings
      .filter(_.keyValuePairs.keyExact(FindingsPass.FileName).exists(_.value == name))
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
        runThorat()
    }
  }

  private def runThorat(): BaseResult = {
    val inputDir = (benchmarkBaseDir / "src").delete(true).createDirectoryIfNotExists()
    (benchmarkBaseDir / "tests").list.foreach(_.copyToDirectory(inputDir))
    if wholeProgram then setupWholeProgram(inputDir)
    try {
      recordTime(() => {
        val expectedTestOutcomes = getExpectedTestOutcomes
        val memoryFuture         = MemoryMonitor.monitorMemoryUsage(MemoryMonitor.getCurrentProcessId)
        val cpgResult =
          try {
            cpgCreator.createCpg(inputDir, cpg => ThoratSourcesAndSinks(cpg))
          } finally {
            MemoryMonitor.stopMeasuring()
          }

        val result = cpgResult match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName benchmark")
            TaintAnalysisResult()
          case Success(cpg) =>
            Using.resource(cpg) { cpg =>
              setCpg(cpg)
              val results = expectedTestOutcomes.collect { case (testFullName, outcome) =>
                TestEntry(testFullName, compare(testFullName, outcome))
              }.toList
              TaintAnalysisResult(results, memory = Await.result(memoryFuture, Duration.Inf).toList)
            }
        }
        val leftoverResults =
          expectedTestOutcomes
            .filter { case (name, outcome) => !result.entries.exists(x => name.startsWith(x.testName)) }
            .map {
              case (name, false) => TestEntry(name, TestOutcome.TN)
              case (name, true)  => TestEntry(name, TestOutcome.FN)
            }
            .l
        result.copy(entries = result.entries ++ leftoverResults)

      })
    } finally {
      if wholeProgram then cleanupWholeProgram(inputDir)
      inputDir.delete(true)
    }
  }

  class ThoratSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    private val candidates: List[TAF] = cpg.file.name.flatMap(testMetaData.get).l

    private val sourceCandidates: List[TAFStatement] =
      candidates
        .flatMap(_.findings)
        .map(_.source)

    private val sinkCandidates: List[TAFStatement] =
      candidates
        .flatMap(_.findings)
        .map(_.sink)

    override def sources: Iterator[CfgNode] = {
      val filenameString = candidates.map(_.fileName).mkString(",")
      sourceCandidates.flatMap { case TAFStatement(_, methodName, _, lineNo, targetName, _) =>
        val methodTrav = {
          val m = cpg.method.nameExact(methodName).l
          m ++ m.astChildren.isMethod.l
        }
        if (methodTrav.isEmpty) {
          logger.warn(s"Unable to match source method for '$methodName' among [$filenameString]")
          Iterator.empty
        } else {
          val targetTrav = targetName match {
            case "" => methodTrav.call.assignment.source.lineNumber(lineNo).l
            case callName =>
              methodTrav.call.and(_.lineNumber(lineNo), _.or(_.nameExact(callName), _.code(s".*$callName"))).l
          }
          if (targetTrav.isEmpty) {
            logger.warn(s"Unable to match source target for '$targetName' among [$filenameString]")
          }
          targetTrav
        }
      }.iterator
    }

    override def sinks: Iterator[CfgNode] = {
      val filenameString = candidates.map(_.fileName).mkString(",")
      sinkCandidates.flatMap { case TAFStatement(_, methodName, _, lineNo, targetName, _) =>
        val methodTrav = {
          val m = cpg.method.nameExact(methodName).l
          m ++ m.astChildren.isMethod.l
        }
        if (methodTrav.isEmpty) {
          logger.warn(s"Unable to match sink method for '$methodName' among [$filenameString]")
          Iterator.empty
        } else {
          val targetTrav = targetName match {
            case ""       => methodTrav.call.assignment.source.lineNumber(lineNo).l
            case callName =>
              // Tags calls and method refs (which would be identifiers for external method refs)
              methodTrav.call.argument.isIdentifier.and(_.lineNumber(lineNo), _.nameExact(callName)).l ++
                methodTrav.call
                  .and(_.lineNumber(lineNo), _.or(_.nameExact(callName), _.code(s".*$callName")))
                  .argument
                  .argumentIndexGt(0)
                  .l
          }
          if (targetTrav.isEmpty) {
            logger.warn(s"Unable to match sink target for '$targetName' among [$filenameString]")
          }
          targetTrav
        }
      }.iterator
    }

  }

}
