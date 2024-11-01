package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.PySrcCpgCreator
import io.joern.benchmarks.runner.*
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.language.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*

import scala.collection.mutable
import scala.util.{Failure, Success}

class BugsInPyJoernRunner(datasetDir: File, cpgCreator: PySrcCpgCreator)
    extends BugsInPyRunner(datasetDir, cpgCreator.frontend, cpgCreator.maxCallDepth) {

  override def runIteration: BaseResult = {
    val entries = mutable.ArrayBuffer.empty[PerfRun]
    packageNames.foreach { packageName =>
      val inputDir = benchmarkBaseDir / packageName
      recordTime(() => cpgCreator.createCpg(inputDir, cpg => BugsInPySourcesAndSinks(cpg))) match {
        case Failure(exception) =>
          logger.error(s"Unable to generate CPG for $benchmarkName/$packageName", exception)
        case Success(cpg) =>
          entries.addOne(PerfRun(packageName, getTimeSeconds))
      }
    }
    PerformanceTestResult(entries.toList, k = cpgCreator.maxCallDepth)
  }

  class BugsInPySourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    override def sources: Iterator[CfgNode] = {
      val builtins = cpg.call.methodFullName(
        "__builtins__\\..*input.*",
        "os\\..*(getenv|environ).*",
        "sys\\..*(argv|stdin\\.read).*",
        "socket\\..*\\.recv.*",
        "http\\..*server\\..*BaseHTTPRequestHandler.*",
        "email\\..*parser\\..*Parser.*",
        "(json|pickle)\\..*loads?.*",
        "csv\\..*reader.*",
        "xml\\..*etree\\..*ElementTree\\..*parse.*"
      )

      val webFrameworks = cpg.call.methodFullName(
        "flask\\..*(request|response)\\..(args|form|json).*",
        "django\\..*http\\..*Http(Request|Response)\\..*(GET|POST|body).*"
      )

      val miscLibs = cpg.call.methodFullName(
        "requests\\..*(get|post|request)\\..*",
        "paramiko\\..*SSHClient\\..*exec_command.*",
        "(pymysql|psycopg2)\\.*(connect|cursor|execute).*"
      )

      builtins ++ webFrameworks ++ miscLibs
    }

    override def sinks: Iterator[CfgNode] = {
      val builtins = cpg.call.methodFullName(
        "os\\..*(system|popen).*",
        "subprocess\\..*(Popen|run|call).*",
        "__builtins__\\..*(exec|eval|open|print).*",
        "shutil\\..*(copyfile|move).*",
        "pickle\\..*dumps?\\..*",
        "sqlite3\\..*execute.*",
        "xml\\..*etree\\..*ElementTree\\..*write.*",
        "logging\\..*Logger\\..*(info|warn|error).*"
      )

      val webFrameworks = cpg.call.methodFullName(
        "flask\\..*(Response|render\\_template).*",
        "django\\..*http\\..*HttpResponse.*",
        "django\\..*shortcuts\\..*render.*",
        "django\\..*db\\..*connection\\..*execute.*",
        "jinja2\\..*template\\..*render.*"
      )

      val miscLibs = cpg.call.methodFullName(
        "requests\\..*(put|post|request)\\..*",
        "paramiko\\..*SSHClient\\..*exec_command.*",
        "(pymysql|psycopg2|sqlalchemy)\\.*(connect|cursor|execute).*"
      )

      (builtins ++ webFrameworks ++ miscLibs).argument.argumentIndexGte(1)
    }

  }

}
