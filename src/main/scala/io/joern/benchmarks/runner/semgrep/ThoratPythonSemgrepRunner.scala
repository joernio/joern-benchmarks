package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.runner.*
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner.SemGrepTrace
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.semanticcpg.language.*

import scala.util.{Failure, Success, Using}

class ThoratPythonSemgrepRunner(datasetDir: File)
    extends ThoratPythonRunner(datasetDir, SemgrepBenchmarkRunner.CreatorLabel)
    with SemgrepBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    val res                = sgResults.results
    val filter = sgResults.results
      .flatMap(_.extra.dataflowTrace)
      .filter { case SemGrepTrace((_, (sinkLoc, _)), _) =>
        sinkLoc.path.endsWith(name) && sinkLoc.start.line == lineNo.toInt
      }
      .map(_ => FindingInfo())
      .toList
    filter
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runThorat()
    }
  }

  private def runThorat(): Result = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    val rules = Option(getClass.getResourceAsStream("/semgrep/ThoratRules.yaml")) match {
      case Some(res) =>
        Using.resource(res) { is =>
          Option {
            File
              .newTemporaryFile("joern-benchmarks-semrep-", ".yaml")
              .deleteOnExit(swallowIOExceptions = true)
              .writeByteArray(is.readAllBytes())
          }
        }
      case None =>
        logger.error(s"Unable to fetch Semgrep rules for $benchmarkName")
        None
    }
    runScan(benchmarkBaseDir / "tests", Seq("--include=*.py"), rules) match {
      case Failure(exception) =>
        logger.error(s"Error encountered while running `semgrep` on $benchmarkName", exception)
        Domain.Result()
      case Success(semgrepResults) =>
        setResults(semgrepResults)
        val testResults = expectedTestOutcomes
          .map { case (testName, outcome) =>
            TestEntry(testName, compare(testName, outcome))
          }
          .toList
          .sortBy(_.testName)
        Domain.Result(testResults)
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
        val methodTrav = cpg.method.nameExact(methodName).l
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
        val methodTrav = cpg.method.nameExact(methodName).l
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
