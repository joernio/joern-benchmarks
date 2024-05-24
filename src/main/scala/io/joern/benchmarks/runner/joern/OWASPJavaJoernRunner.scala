package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JavaCpgCreator, JsSrcCpgCreator}
import io.joern.benchmarks.passes.{FindingsPass, JavaTaggingPass}
import io.joern.benchmarks.runner.{
  BenchmarkRunner,
  CompressionTypes,
  CpgBenchmarkRunner,
  OWASPJavaRunner,
  SingleFileDownloader
}
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.X2CpgFrontend
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import org.slf4j.LoggerFactory

import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI, URL}
import scala.util.{Failure, Success, Try, Using}
import scala.xml.XML

class OWASPJavaJoernRunner(datasetDir: File, cpgCreator: JavaCpgCreator[?])
    extends OWASPJavaRunner(datasetDir, cpgCreator.frontend)
    with CpgBenchmarkRunner {

  override def findings(testName: String): List[Finding] = {
    cpg.findings
      .filter(_.keyValuePairs.keyExact(FindingsPass.SurroundingType).exists(_.value == testName))
      .l
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runOWASP()
    }
  }

  private def runOWASP(): Result = {
    val expectedTestOutcomes = getExpectedTestOutcomes
    cpgCreator.createCpg(benchmarkBaseDir) match {
      case Failure(exception) =>
        logger.error(s"Unable to generate CPG for $benchmarkName benchmark")
        Result()
      case Success(cpg) =>
        Using.resource(cpg) { cpg =>
          setCpg(cpg)
          val testResults = expectedTestOutcomes
            .map { case (testName, outcome) =>
              TestEntry(testName, compare(testName, outcome))
            }
            .sortBy(_.testName)
            .l
          Result(testResults)
        }
    }
  }

}
