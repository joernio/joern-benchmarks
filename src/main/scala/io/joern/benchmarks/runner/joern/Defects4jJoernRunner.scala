package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.JavaCpgCreator
import io.joern.benchmarks.runner.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.language.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*

import scala.collection.mutable
import scala.util.{Failure, Success}

class Defects4jJoernRunner(datasetDir: File, cpgCreator: JavaCpgCreator[?])
    extends Defects4jRunner(datasetDir, cpgCreator.frontend, cpgCreator.maxCallDepth) {

  override def runIteration: BaseResult = {
    val entries = mutable.ArrayBuffer.empty[PerfRun]
    packageNames.foreach { packageName =>
      val inputDir = benchmarkBaseDir / packageName
      recordTime(() => cpgCreator.createCpg(inputDir, cpg => Defects4jSourcesAndSinks(cpg))) match {
        case Failure(exception) =>
          logger.error(s"Unable to generate CPG for $benchmarkName/$packageName", exception)
        case Success(cpg) =>
          if (cpg.findings.isEmpty) logger.warn(s"No findings for $packageName!")
          entries.addOne(PerfRun(packageName, getTimeSeconds))
      }
    }
    PerformanceTestResult(entries.toList, k = cpgCreator.maxCallDepth)
  }

  private class Defects4jSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    override def sources: Iterator[CfgNode] = {
      val builtins = cpg.call.methodFullName(
        "java\\.lang\\.System.*get(env|Property).*",
        "java\\.lang\\.Runtime.*exec.*",
        "java\\.io\\.BufferedReader.*readLine.*",
        "java\\.io\\.(Input|File|Object|ByteArray)Stream.*read.*",
        "java.\\nio\\.file\\.Files.*readAllBytes.*",
        "java\\.util\\.Scanner.*nextLine.*",
        "java\\.net\\.Socket.*getInputStream.*"
      )

      val servletsAndWeb = cpg.call.methodFullName(
        "javax\\.servlet\\.http\\.HttpServletRequest.*get(Cookies|Header|InputStream|Parameter|QueryString).*"
      )

      val jdbc =
        cpg.call.methodFullName("java\\.sql\\.Connection.*prepareStatement.*", "java\\.sql\\.Statement.*executeQuery.*")

      val misc = cpg.call.methodFullName(
        "org\\.apache\\.http\\.client\\.HttpClient.*execute.*",
        "com\\.fasterxml\\.jackson\\.databind\\.ObjectMapper.*readValue.*",
        "org\\.apache\\.commons\\.io\\.FileUtils.*readFileToString.*"
      )

      val apacheCommons =
        cpg.typeDecl
          .nameExact("Transformer", "Closure")
          .derivedTypeDeclTransitive
          .method
          .nameExact("<init>")
          .parameter

      val apacheCsv = cpg.typeDecl
        .nameExact("CSVPrinter", "CSVFormat", "CSVRecord")
        .method
        .nameExact("print", "printRecord", "withHeader", "get", "isMapped")
        .parameter
        .index(1)

      builtins ++ servletsAndWeb ++ jdbc ++ misc ++ apacheCommons ++ apacheCsv
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
        "javax\\.servlet\\.RequestDispatcher.*(forward|include).*"
      )

      val jdbc = cpg.call.methodFullName(
        "java\\.sql\\.Connection.*createStatement.*",
        "java\\.sql\\.Statement.*execute.*",
        "java\\.sql\\.Statement.*executeQuery.*"
      )

      val jdbcMisusePreparedStmt = cpg.call.methodFullName("java\\.sql\\.Connection.*prepareStatement.*").argument(1)

      val misc = cpg.call.methodFullName(
        "org\\.apache\\.commons\\.io\\.FileUtils.*writeStringToFile.*",
        "com\\.fasterxml\\.jackson\\.databind\\.ObjectMapper.*writeValue.*",
        "org\\.apache\\.velocity\\.Template.*merge.*"
      )

      val gson = (cpg.typeDecl.nameExact("Gson").method.nameExact("fromJson") ++ cpg.typeDecl
        .nameExact("JsonDeserializer")
        .derivedTypeDeclTransitive
        .method
        .nameExact("deserialize")).parameter.index(1).argument

      val apacheCsv = cpg.typeDecl
        .nameExact("CSVPrinter", "CSVFormat", "CSVRecord")
        .method
        .nameExact("print", "printRecord", "withHeader", "get", "isMapped")
        .parameter
        .index(1)
        .argument

      (builtins ++ servletsAndWeb ++ jdbc ++ misc).argument
        .argumentIndexGte(1) ++ jdbcMisusePreparedStmt ++ gson // ++ apacheCsv
    }

  }

}
