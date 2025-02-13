package io.joern.benchmarks.runner.semgrep

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.{TaintAnalysisResult, TestEntry}
import io.joern.benchmarks.runner.semgrep.SemgrepBenchmarkRunner.SemGrepTrace
import io.joern.benchmarks.runner.{FindingInfo, SecuribenchMicroJsRunner}

import scala.util.{Failure, Success}

class SecuribenchMicroJsSemgrepRunner(datasetDir: File, wholeProgram: Boolean)
    extends SecuribenchMicroJsRunner(datasetDir, SemgrepBenchmarkRunner.CreatorLabel)
    with SemgrepBenchmarkRunner {

  override protected def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    sgResults.results
      .flatMap(_.extra.dataflowTrace)
      .filter { case SemGrepTrace((_, (sinkLoc, _)), _) =>
        val testNameFromFile = fileNameToTestName(sinkLoc.path)
        testNameFromFile.startsWith(name) && testNameFromFile.endsWith(lineNo)
      }
      .map(_ => FindingInfo())
      .toList
  }

  override def runIteration: Domain.BaseResult = {
    val rules = getRules("SecuribenchMicroJsRules")
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        TaintAnalysisResult()
      case Success(_) =>
        val inputDir = benchmarkBaseDir / s"securibench-micro.js-$version"
        if wholeProgram then setupWholeProgram(inputDir)
        try {
          runScan(inputDir, Seq.empty, rules) match {
            case Failure(exception) =>
              logger.error(s"Error encountered while running `semgrep` on $benchmarkName", exception)
              Domain.TaintAnalysisResult()
            case Success((semgrepResults, memory)) =>
              setResults(semgrepResults)
              val expectedTestOutcomes = getExpectedTestOutcomes
              val testResults = expectedTestOutcomes
                .map { case (testName, outcome) =>
                  TestEntry(testName, compare(testName, outcome))
                }
                .toList
                .sortBy(_.testName)
              Domain.TaintAnalysisResult(testResults, memory = memory)
          }
        } finally {
          if wholeProgram then cleanupWholeProgram(inputDir)
        }
    }
  }

}
