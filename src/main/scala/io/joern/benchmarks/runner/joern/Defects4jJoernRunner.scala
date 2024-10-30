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
import scala.collection.mutable
import scala.util.{Failure, Success, Try, Using}

class Defects4jJoernRunner(datasetDir: File, cpgCreator: JavaCpgCreator[?])
    extends Defects4jRunner(datasetDir, cpgCreator.frontend, cpgCreator.maxCallDepth) {

  override def runIteration: BaseResult = {
    val entries = mutable.ArraySeq.empty[PerfRun]
    packageNames
      .map { packageName =>
        val inputDir = benchmarkBaseDir / packageName
        recordTime(() => cpgCreator.createCpg(inputDir, cpg => Defects4jSourcesAndSinks(cpg))) match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName/$packageName", exception)
            PerfRun(packageName, -1L)
          case Success(cpg) =>
            PerfRun(packageName, getTimeSeconds)
        }
      }
    PerformanceTestResult(entries.toList, k = cpgCreator.maxCallDepth)
  }

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
