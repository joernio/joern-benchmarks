package io.joern.benchmarks.runner.joern

import better.files.File
import com.github.sh4869.semver_parser.{Range, SemVer}
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.JavaScriptCpgCreator
import io.joern.benchmarks.runner.*
import io.joern.dataflowengineoss.language.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.codepropertygraph.generated.{Cpg, Operators}
import io.shiftleft.codepropertygraph.generated.language.*
import io.shiftleft.semanticcpg.language.*

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try, Using}

class IchnaeaJoernRunner(datasetDir: File, cpgCreator: JavaScriptCpgCreator[?])
    extends IchnaeaRunner(datasetDir, cpgCreator.frontend)
    with CpgBenchmarkRunner {

  override def findings(testName: String): List[FindingInfo] = {
    cpg.findings.map(mapToFindingInfo).l
  }

  override def runIteration: BaseResult = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        TaintAnalysisResult()
      case Success(benchmarkDir) =>
        runIchnaea()
    }
  }

  private def runIchnaea(): BaseResult = recordTime(() => {
    val outcomes = getExpectedTestOutcomes
    packageNames
      .map { packageName =>
        val inputDir     = benchmarkBaseDir / packageName / "package"
        val memoryFuture = MemoryMonitor.monitorMemoryUsage(MemoryMonitor.getCurrentProcessId)
        val result =
          try {
            cpgCreator.createCpg(inputDir, cpg => IchnaeaSourcesAndSinks(cpg))
          } finally {
            MemoryMonitor.stopMeasuring()
          }
        result match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName/$packageName", exception)
            TaintAnalysisResult()
          case Success(cpg) =>
            Using.resource(cpg) { cpg =>
              setCpg(cpg)
              TaintAnalysisResult(
                TestEntry(packageName, compare(packageName, outcomes(packageName))) :: Nil,
                memory = Await.result(memoryFuture, Duration.Inf).toList
              )
            }
        }
      }
      .foldLeft(TaintAnalysisResult())(_ ++ _)
  })

  class IchnaeaSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    override def sources: Iterator[CfgNode] = {
      // Many libraries export functions which we consider the parameters of to be "attacker-controlled".
      val exposeFunctionSink = cpg.method
        .nameExact(Operators.indexAccess, Operators.fieldAccess)
        .callIn
        .inAssignment
        .code("(:?module.)?exports.*")
        .source
        .l

      // Overapproximate similar to what is done in the CodeQL query
      val mayBeExposed = exposeFunctionSink.file.method.l

      // e.g. val func = function (x) {}; module.exports = func
      val exposedObjectsSource = exposeFunctionSink
        .reachableBy(cpg.identifier.where(_.inAssignment.source.isMethodRef))
        .inAssignment
        .source
        .isMethodRef
        .referencedMethod
        .l

      // e.g. func.utils.foo = function() {}; module.exports = func
      val fieldsOfExposedObjects = cpg.fieldAccess
        .where(_.argument(1).code(exposeFunctionSink.isIdentifier.map(x => s"${x.code}.*").toSeq*))
        .inAssignment
        .source
        .isMethodRef
        .referencedMethod
        .l

      def findExposedMethods(m: Method): Iterator[Method] = {
        val assignedMethodRefs = m.assignment.source.isMethodRef
        m ++ (m.methodReturn.toReturn ++ exposeFunctionSink)
          .reachableBy(assignedMethodRefs)
          .isMethodRef
          .referencedMethod
          .flatMap(findExposedMethods)
      }

      val possiblyExposedFunctions = exposeFunctionSink
        .flatMap {
          // Blocks are used in object constructors e.g. module.exports = { x = function(e) {} }
          case x: Block     => x.assignment.source.isMethodRef
          case x: MethodRef => Iterator(x)
          case _            => Iterator.empty
        }
        .referencedMethod
        .flatMap(findExposedMethods)
        .l

      val assignedToExportedObject = // Handles `module.exports = new(function() { this.foo = function() })()`
        exposeFunctionSink.isBlock.astChildren.isCall.callee.fieldAccess
          .code("this.*")
          .inAssignment
          .source
          .isMethodRef
          .referencedMethod
          .flatMap(findExposedMethods)
          .l

      val allExposedMethods =
        (possiblyExposedFunctions ++ exposedObjectsSource ++ assignedToExportedObject ++ fieldsOfExposedObjects ++ mayBeExposed).l
      val exposedLocalsViaCapture = allExposedMethods._refIn // no great way to do this yet
        .collectAll[MethodRef]
        .out("CAPTURE")
        .out
        .collectAll[Local]
        .referencingIdentifiers
        .l
      allExposedMethods.parameter.indexGt(0) ++ exposedLocalsViaCapture
    }

    override def sinks: Iterator[CfgNode] = {
      // Vulnerable version of growl
      val growlCall = {
        cpg.dependency
          .nameExact("growl")
          .filterNot(d => Try(SemVer(d.version.stripPrefix("~"))).isFailure)
          .headOption match {
          case Some(growlDep) if Range(">1.9.2").invalid(SemVer(growlDep.version.stripPrefix("~"))) =>
            cpg.call.nameExact("growl").argument
          case _ => Iterator.empty
        }
      }
      val otherSinks = cpg.call.nameExact("exec", "eval", "execSync", "execFileSync").argument(1)
      growlCall ++ otherSinks
    }

  }

}
