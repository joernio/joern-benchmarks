package io.joern.benchmarks

import io.joern.benchmarks.Benchmark.benchmarkConstructors
import io.joern.benchmarks.formatting.formatterConstructors
import io.joern.benchmarks.runner.BenchmarkRunner
import org.slf4j.LoggerFactory
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JVMBytecodeCpgCreator, JavaSrcCpgCreator, JsSrcCpgCreator, PySrcCpgCreator}
import io.joern.benchmarks.runner.codeql.{IchnaeaCodeQLRunner, SecuribenchMicroCodeQLRunner, ThoratCodeQLRunner}
import io.joern.benchmarks.runner.joern.{IchnaeaJoernRunner, SecuribenchMicroJoernRunner, ThoratJoernRunner}
import io.joern.benchmarks.runner.semgrep.{IchnaeaSemgrepRunner, SecuribenchMicroSemgrepRunner, ThoratSemgrepRunner}
import org.slf4j.LoggerFactory
import upickle.default.*

/** The main benchmarking process.
  */
class Benchmark(config: BenchmarkConfig) {

  private val logger = LoggerFactory.getLogger(getClass)

  /** The benchmarking entrypoint.
    */
  def evaluate(): Unit = {
    logger.info("Beginning evaluation")

    def runBenchmark(benchmarkRunnerCreator: BenchmarkConfig => BenchmarkRunner): Unit = {
      val benchmarkRunner = benchmarkRunnerCreator(config)
      val benchmarkName   = benchmarkRunner.benchmarkName
      logger.info(s"Running ${config.benchmark} using ${config.frontend}")
      benchmarkRunner.run(config.iterations) match {
        case Result(Nil, _) => logger.warn(s"Empty results for $benchmarkName")
        case result =>
          val targetOutputFile =
            config.outputDir / benchmarkName.replace(' ', '_') createDirectoryIfNotExists (createParents = true)
          formatterConstructors
            .get(config.outputFormat)
            .foreach(_.apply(result).writeTo(targetOutputFile))
      }
    }

    val benchmarkFrontendKey = config.benchmark -> config.frontend
    benchmarkConstructors.get(benchmarkFrontendKey) match {
      case Some(runner) => runBenchmark(runner)
      case None         => logger.error(s"Unsupported benchmark/frontend combination: $benchmarkFrontendKey")
    }
  }

}

object Benchmark {

  val benchmarkConstructors
    : Map[(AvailableBenchmarks.Value, AvailableFrontends.Value), BenchmarkConfig => BenchmarkRunner] = Map(
    (
      AvailableBenchmarks.SECURIBENCH_MICRO -> AvailableFrontends.JAVASRC,
      x => new SecuribenchMicroJoernRunner(x.datasetDir, JavaSrcCpgCreator(x.disableSemantics, x.maxCallDepth))
    ),
    (
      AvailableBenchmarks.SECURIBENCH_MICRO -> AvailableFrontends.JAVA,
      x => new SecuribenchMicroJoernRunner(x.datasetDir, JVMBytecodeCpgCreator(x.disableSemantics, x.maxCallDepth))
    ),
    (
      AvailableBenchmarks.ICHNAEA -> AvailableFrontends.JSSRC,
      x => new IchnaeaJoernRunner(x.datasetDir, JsSrcCpgCreator(x.disableSemantics, x.maxCallDepth))
    ),
    (
      AvailableBenchmarks.THORAT -> AvailableFrontends.PYSRC,
      x => new ThoratJoernRunner(x.datasetDir, PySrcCpgCreator(x.disableSemantics, x.maxCallDepth))
    ),
    (
      AvailableBenchmarks.SECURIBENCH_MICRO -> AvailableFrontends.SEMGREP,
      x => new SecuribenchMicroSemgrepRunner(x.datasetDir)
    ),
    (AvailableBenchmarks.THORAT  -> AvailableFrontends.SEMGREP, x => new ThoratSemgrepRunner(x.datasetDir)),
    (AvailableBenchmarks.ICHNAEA -> AvailableFrontends.SEMGREP, x => new IchnaeaSemgrepRunner(x.datasetDir)),
    (
      AvailableBenchmarks.SECURIBENCH_MICRO -> AvailableFrontends.CODEQL,
      x => new SecuribenchMicroCodeQLRunner(x.datasetDir)
    ),
    (AvailableBenchmarks.THORAT  -> AvailableFrontends.CODEQL, x => new ThoratCodeQLRunner(x.datasetDir)),
    (AvailableBenchmarks.ICHNAEA -> AvailableFrontends.CODEQL, x => new IchnaeaCodeQLRunner(x.datasetDir))
  )

}
