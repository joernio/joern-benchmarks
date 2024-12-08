package io.joern.benchmarks.runner.codeql

import better.files.File
import io.joern.benchmarks.Domain
import io.joern.benchmarks.Domain.TestEntry
import io.joern.benchmarks.runner.{FindingInfo, SecuribenchMicroRunner}
import io.joern.benchmarks.runner.codeql.CodeQLBenchmarkRunner.CodeQLSimpleResult

import scala.util.{Failure, Success}

class SecuribenchMicroCodeQLRunner(datasetDir: File, wholeProgram: Boolean)
    extends SecuribenchMicroRunner(datasetDir, CodeQLBenchmarkRunner.CreatorLabel)
    with CodeQLBenchmarkRunner {

  override protected def findings(testName: String): List[FindingInfo] = {
    val List(name, lineNo) = testName.split(':').toList: @unchecked
    cqlResults.findings
      .filter { case CodeQLSimpleResult(filename, lineNumber) =>
        filename.stripSuffix(".java").endsWith(name) && lineNumber == lineNo.toInt
      }
      .map(_ => FindingInfo())
  }

  override def runIteration: Domain.BaseResult = {
    val rules = getRules("SecuribenchMicro").toList
    if wholeProgram then {
      (benchmarkBaseDir / "build.gradle").writeText(SecuribenchMicroCodeQLRunner.buildGradle)
    }
    runScan(benchmarkBaseDir, "java", rules, autobuild = wholeProgram) match {
      case Failure(exception) =>
        logger.error(s"Error encountered while running `codeql` on $benchmarkName", exception)
        Domain.TaintAnalysisResult()
      case Success(codeQlResults) =>
        setResults(codeQlResults)
        val testResults = getExpectedTestOutcomes
          .map { case (testName, outcome) =>
            TestEntry(testName, compare(testName, outcome))
          }
          .toList
          .sortBy(_.testName)
        Domain.TaintAnalysisResult(testResults)
    }
  }

}

object SecuribenchMicroCodeQLRunner {

  private val buildGradle =
    """
      |plugins {
      |    id 'java'
      |}
      |
      |group = 'securibench.micro'
      |version = '1.0.0'
      |
      |repositories {
      |    mavenCentral()
      |    flatDir {
      |        dirs 'lib' // Include JARs in the 'lib' directory
      |    }
      |}
      |
      |dependencies {
      |    implementation fileTree(dir: 'lib', include: '**/*.jar') // Include all JARs in the 'lib' directory
      |    implementation files('lib/j2ee.jar')
      |
      |    // XDoclet-related JARs
      |    implementation files('lib/xdoclet/xdoclet-1.2.3.jar')
      |    implementation files('lib/xdoclet/xdoclet-web-module-1.2.3.jar')
      |    implementation files('lib/xdoclet/xjavadoc-1.1.jar')
      |    implementation files('lib/xdoclet/commons-collections-2.0.jar')
      |    implementation files('lib/xdoclet/commons-logging.jar')
      |    implementation files('lib/xdoclet/log4j.jar')
      |}
      |
      |sourceSets {
      |    main {
      |        java {
      |            srcDirs = ['src'] // Source files location
      |        }
      |    }
      |}
      |
      |""".stripMargin

}
