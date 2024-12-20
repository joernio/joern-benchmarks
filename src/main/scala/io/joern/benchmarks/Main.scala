package io.joern.benchmarks

import better.files.File
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.X2Cpg.applyDefaultOverlays
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.passes.CpgPass
import io.shiftleft.semanticcpg.language.*
import org.slf4j.LoggerFactory
import scopt.OptionParser

import scala.util.{Failure, Success}

/** Example program that makes use of Joern as a library */
object Main {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    optionParser.parse(args, BenchmarkConfig()).map(Benchmark(_)).foreach(_.evaluate())
  }

  private val optionParser: OptionParser[BenchmarkConfig] = new OptionParser[BenchmarkConfig]("joern-benchmark") {

    implicit val availableBenchmarksRead: scopt.Read[AvailableBenchmarks.Value] =
      scopt.Read.reads(AvailableBenchmarks withName _)

    implicit val availableFrontendsRead: scopt.Read[AvailableFrontends.Value] =
      scopt.Read.reads(AvailableFrontends withName _)

    implicit val outputFormatRead: scopt.Read[OutputFormat.Value] =
      scopt.Read.reads(OutputFormat withName _)

    implicit val betterFilesRead: scopt.Read[File] =
      scopt.Read.reads(File.apply(_))

    head("joern-benchmark", ManifestVersionProvider().getVersion)

    note("A benchmarking suite for Joern")
    help('h', "help")
    version("version").text("Prints the version")

    arg[AvailableBenchmarks.Value]("benchmark")
      .text(s"The benchmark to run. Available [${AvailableBenchmarks.values.mkString(",")}]")
      .required()
      .action((x, c) => c.copy(benchmark = x))
    arg[AvailableFrontends.Value]("frontend")
      .text(s"The frontend to use. Available [${AvailableFrontends.values.mkString(",")}]")
      .required()
      .action((x, c) => c.copy(frontend = x))
    opt[File]('d', "dataset-dir")
      .text("The dataset directory where benchmarks will be initialized and executed. Default is `./workspace`.")
      .action { (x, c) =>
        x.createDirectoryIfNotExists(createParents = true)
        c.copy(datasetDir = x)
      }
    opt[File]('o', "output")
      .text("The output directory to write results to. Default is `./results`.")
      .action { (x, c) =>
        x.createDirectoryIfNotExists(createParents = true)
        c.copy(outputDir = x)
      }
    opt[OutputFormat.Value]('f', "format")
      .text(s"The output format to write results as. Default is MD. Available [${OutputFormat.values.mkString(",")}]")
      .action((x, c) => c.copy(outputFormat = x))
    opt[Unit]("disable-semantics")
      .text(s"Disables the user-defined semantics for Joern data-flows. Has no effect on non-Joern frontends.")
      .action((x, c) => c.copy(disableSemantics = true))
    opt[Int]('k', "max-call-depth")
      .text("The max call depth `k` for the data-flow engine. Has no effect on non-Joern frontends. Default is 5.")
      .validate {
        case x if x < 0 => failure("Max call depth must be greater than or equal to 0.")
        case _          => success
      }
      .action((x, c) => c.copy(maxCallDepth = x))
    opt[Int]('i', "iterations")
      .text("The number of iterations for a given benchmark. Default is 1.")
      .validate {
        case x if x <= 0 => failure("Iterations must be greater than 0.")
        case _           => success
      }
      .action((x, c) => c.copy(iterations = x))
    opt[Unit]('w', "whole-program")
      .text("Enables whole program analysis. Off by default.")
      .action((_, c) => c.copy(wholeProgram = true))
  }

}
