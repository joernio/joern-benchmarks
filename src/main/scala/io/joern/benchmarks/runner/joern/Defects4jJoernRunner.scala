package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JavaCpgCreator, JavaScriptCpgCreator, PySrcCpgCreator}
import io.joern.benchmarks.runner.*
import io.joern.dataflowengineoss.language.*
import io.shiftleft.codepropertygraph.generated.language.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.codepropertygraph.generated.{Cpg, Operators}
import io.shiftleft.semanticcpg.language.*

import scala.collection.immutable.List
import scala.util.{Failure, Success, Try, Using}

class Defects4jJoernRunner(datasetDir: File, cpgCreator: JavaCpgCreator[?])
    extends Defects4jRunner(datasetDir, cpgCreator.frontend)
    with CpgBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    cpg.findings.map(mapToFindingInfo).l
  }

  override def runIteration: Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runDefects4j()
    }
  }

  private def runDefects4j(): Result = recordTime(() => {
    val outcomes = getExpectedTestOutcomes
    packageNames
      .map { packageName =>
        val inputDir = benchmarkBaseDir / packageName
        cpgCreator.createCpg(inputDir, cpg => Defects4jSourcesAndSinks(cpg)) match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName/$packageName", exception)
            Result()
          case Success(cpg) =>
            Using.resource(cpg) { cpg =>
              setCpg(cpg)
              Result(TestEntry(packageName, compare(packageName, outcomes(packageName))) :: Nil)
            }
        }
      }
      .foldLeft(Result())(_ ++ _)
  })

  class Defects4jSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    override def sources: Iterator[CfgNode] = {
      val builtins = cpg.call.methodFullName(
        "java\\.lang\\.System.*get(env|Property).*",
        "java\\.lang\\.Runtime.*exec.*",
        "java\\.io\\.BufferedReader.*readLine.*",
        "java\\.io\\.InputStream.*read.*",
        "java.\\nio\\.file\\.Files.*readAllBytes.*",
        "java\\.util\\.Scanner.*nextLine.*",
        "java\\.net\\.Socket.*getInputStream.*"
      )

      val servletsAndWeb = cpg.call.methodFullName(
        "javax\\.servlet\\.http\\.HttpServletRequest.*(getParameter|getQueryString|getHeader|getInputStream|getCookies).*",
        "org\\.springframework\\.web\\.bind\\.annotation\\.(RequestParam|RequestBody).*",
        "org\\.springframework\\.web\\.context\\.request\\.RequestAttributes.*getAttribute.*"
      )

      val jdbc =
        cpg.call.methodFullName("java\\.sql\\.Connection.*prepareStatement.*", "java\\.sql\\.Statement.*executeQuery.*")

      val misc = cpg.call.methodFullName(
        "org\\.apache\\.http\\.client\\.HttpClient.*execute.*",
        "com\\.fasterxml\\.jackson\\.databind\\.ObjectMapper.*readValue.*",
        "org\\.apache\\.commons\\.io\\.FileUtils.*readFileToString.*"
      )

      builtins ++ servletsAndWeb ++ jdbc ++ misc
    }

    override def sinks: Iterator[CfgNode] = {
      val builtins = cpg.call.methodFullName(
        "java\\.lang\\.Runtime.*exec.*",
        "java\\.io\\.FileWriter.*write.*",
        "java\\.nio\\.file\\.Files.*write.*",
        "java\\.io\\.PrintWriter.*(println|write).*",
        "javax\\.xml\\.transform\\.Transformer.*transform.*"
      )

      val servletsAndWeb = cpg.call.methodFullName(
        "javax\\.servlet\\.http\\.HttpServletResponse.*addHeader.*",
        "javax\\.servlet\\.RequestDispatcher.*(forward|include).*",
        "org\\.springframework\\.jdbc\\.core\\.JdbcTemplate.*execute.*",
        "org\\.springframework\\.web\\.servlet\\.view\\.JstlView.*render.*",
        "org\\.springframework\\.http\\.ResponseEntity.*",
        "org\\.springframework\\.web\\.servlet\\.ModelAndView.*"
      )

      val jdbc = cpg.call.methodFullName(
        "java\\.sql\\.Connection.*createStatement.*",
        "java\\.sql\\.Statement.*execute.*",
        "java\\.sql\\.Statement.*executeQuery.*"
      )

      val misc = cpg.call.methodFullName(
        "org\\.apache\\.commons\\.io\\.FileUtils.*writeStringToFile.*",
        "com\\.fasterxml\\.jackson\\.databind\\.ObjectMapper.*writeValue.*",
        "org\\.apache\\.velocity\\.Template.*merge.*",
        "org\\.hibernate\\.Session.*createSQLQuery.*"
      )

      (builtins ++ servletsAndWeb ++ jdbc ++ misc).argument
        .argumentIndexGte(1) ++ cpg.call.methodFullName("java\\.sql\\.Connection.*prepareStatement.*").argument(1)
    }

  }

}
