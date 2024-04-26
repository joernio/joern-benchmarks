package io.joern.benchmarks

import better.files.File
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.x2cpg.X2Cpg.applyDefaultOverlays
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.passes.CpgPass
import io.shiftleft.semanticcpg.language.*
import overflowdb.BatchedUpdate
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
      .text(s"The output format to write results as. Default is JSON. Available [${OutputFormat.values.mkString(",")}]")
      .action((x, c) => c.copy(outputFormat = x))
  }

}
