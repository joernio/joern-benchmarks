package io.joern.benchmarks.runner.joern

import better.files.File
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JavaScriptCpgCreator, PySrcCpgCreator}
import io.joern.benchmarks.runner.*
import io.joern.dataflowengineoss.language.*
import io.shiftleft.codepropertygraph.generated.language.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.codepropertygraph.generated.{Cpg, Operators}
import io.shiftleft.semanticcpg.language.*

import scala.collection.immutable.List
import scala.util.{Failure, Success, Try, Using}

class BugsInPyJoernRunner(datasetDir: File, cpgCreator: PySrcCpgCreator)
    extends BugsInPyRunner(datasetDir, cpgCreator.frontend)
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
        runBugsInPy()
    }
  }

  private def runBugsInPy(): Result = recordTime(() => {
    val outcomes = getExpectedTestOutcomes
    packageNames
      .map { packageName =>
        val inputDir = benchmarkBaseDir / packageName / "package"
        cpgCreator.createCpg(inputDir, cpg => BugsInPySourcesAndSinks(cpg)) match {
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
