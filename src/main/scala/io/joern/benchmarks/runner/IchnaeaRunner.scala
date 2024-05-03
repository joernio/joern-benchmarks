package io.joern.benchmarks.runner

import better.files.File
import com.github.sh4869.semver_parser.{SemVer, Range}
import io.joern.benchmarks.*
import io.joern.benchmarks.Domain.*
import io.joern.dataflowengineoss.language.*
import io.joern.benchmarks.cpggen.JavaScriptCpgCreator
import io.shiftleft.codepropertygraph.generated.{Cpg, Operators}
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*
import org.slf4j.LoggerFactory
import upickle.default.*

import java.net.{URI, URL}
import scala.util.{Failure, Success, Try, Using}

class IchnaeaRunner(datasetDir: File, cpgCreator: JavaScriptCpgCreator[?])
    extends BenchmarkRunner(datasetDir)
    with MultiFileDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  override val benchmarkName = s"Ichnaea ${cpgCreator.frontend}"

  private val packageNameAndVersion: Map[String, String] = Map(
    "chook-growl-reporter" -> "0.0.1",
    "cocos-utils"          -> "1.0.0",
    "gm"                   -> "1.20.0",
    "fish"                 -> "0.0.0",
    "git2json"             -> "0.0.1",
    "growl"                -> "1.9.2",
    "libnotify"            -> "1.0.3",
    "m-log"                -> "0.0.1",
    "mixin-pro"            -> "0.6.6",
    "modulify"             -> "0.1.0-1",
    "mongo-parse"          -> "1.0.5",
    "mongoosemask"         -> "0.0.6",
    "mongoosify"           -> "0.0.3",
    "node-os-utils"        -> "1.0.7",
    "node-wos"             -> "0.2.3",
    "office-converter"     -> "1.0.2",
    "os-uptime"            -> "2.0.1",
    "osenv"                -> "0.1.5",
    "pidusage"             -> "1.1.4",
    "pomelo-monitor"       -> "0.3.7",
    "system-locale"        -> "0.1.0",
    "systeminformation"    -> "3.42.2"
  )

  override protected val benchmarkUrls: Map[String, URL] = packageNameAndVersion.flatMap {
    case (packageName, version) =>
      parsePackageArtifactUrl(createNpmJsLookup(packageName, version)) match {
        case Success(distUrl) => Option(packageName -> distUrl)
        case Failure(exception) =>
          logger.error(s"Unable to determine module artifact for $packageName@$version", exception)
          None
      }
  }
  override protected val benchmarkDirName: String = "ichnaea"
  override protected val benchmarkBaseDir: File   = datasetDir / benchmarkDirName

  private def createNpmJsLookup(packageName: String, version: String): URL = URI(
    s"https://registry.npmjs.com/$packageName/$version"
  ).toURL

  private def parsePackageArtifactUrl(registryUrl: URL): Try[URL] = Try {
    Using.resource(registryUrl.openStream()) { is =>
      read[NPMRegistryResponse](ujson.Readable.fromByteArray(is.readAllBytes())).dist.tarball
    }
  }

  override def initialize(): Try[File] = downloadBenchmarkAndUnarchive(CompressionTypes.TGZ)

  override def findings(testName: String)(implicit cpg: Cpg): List[Finding] = {
    cpg.findings.l
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        runIchnaea()
    }
  }

  /** @return
    *   a map with a key of a file name and line number pair, to a boolean indicating true if a the sink is tainted.
    */
  private def getExpectedTestOutcomes: Map[String, Boolean] = {
    // All packages in this dataset have a tainted sink `exec`/`eval`/`execSync`/`execFileSync`
    packageNameAndVersion.keys.map { packageName => packageName -> true }.toMap
  }

  private def runIchnaea(): Result = {
    packageNameAndVersion.keys
      .map { packageName =>
        val inputDir = benchmarkBaseDir / packageName / "package"
        cpgCreator.createCpg(inputDir, cpg => IchnaeaSourcesAndSinks(cpg)) match {
          case Failure(exception) =>
            logger.error(s"Unable to generate CPG for $benchmarkName/$packageName", exception)
            Result()
          case Success(cpg) =>
            Using.resource(cpg) { cpg =>
              if cpg.findings.size > 0 then Result(TestEntry(packageName, TestOutcome.TP) :: Nil)
              else Result(TestEntry(packageName, TestOutcome.FN) :: Nil)
            }
        }
      }
      .foldLeft(Result())(_ ++ _)
  }

  class IchnaeaSourcesAndSinks(cpg: Cpg) extends BenchmarkSourcesAndSinks {

    override def sources: Iterator[CfgNode] = {
      val growlSource = cpg.method.nameExact("growl").parameter
      // Many libraries export functions which we consider the parameters of to be "attacker-controlled".
      val exposeFunctionSink = cpg.method
        .nameExact(Operators.indexAccess, Operators.fieldAccess)
        .callIn
        .code("(:?module.)?exports.*")
        .inAssignment
        .source
        .l

      // e.g. val func = function (x) {}; module.exports = func
      val exposedObjectsSource = exposeFunctionSink
        .reachableBy(cpg.identifier.where(_.inAssignment.source.isMethodRef))
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

      val allExposedMethods = (possiblyExposedFunctions ++ exposedObjectsSource ++ assignedToExportedObject).l
      val exposedLocalsViaCapture = allExposedMethods._refIn // no great way to dot his yet
        .collectAll[MethodRef]
        .outE("CAPTURE")
        .inV
        .outE
        .inV
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
      growlCall
    }

  }

}

implicit val urlRw: ReadWriter[URL] = readwriter[ujson.Value]
  .bimap[URL](
    x => ujson.Str(x.toString),
    {
      case json @ (j: ujson.Str) => URI(json.str).toURL
      case x                     => throw RuntimeException(s"Unexpected value type for URL strings: ${x.getClass}")
    }
  )

case class NPMRegistryResponse(dist: NPMDistBody) derives ReadWriter

case class NPMDistBody(tarball: URL) derives ReadWriter
