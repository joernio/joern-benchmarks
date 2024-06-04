package io.joern.benchmarks

import io.joern.benchmarks.Benchmark.benchmarkConstructors
import io.joern.benchmarks.formatting.formatterConstructors
import io.joern.benchmarks.runner.BenchmarkRunner
import org.slf4j.LoggerFactory
import io.joern.benchmarks.Domain.*
import io.joern.benchmarks.cpggen.{JVMBytecodeCpgCreator, JavaSrcCpgCreator, JsSrcCpgCreator, PySrcCpgCreator}
import io.joern.benchmarks.runner.joern.{
  IchnaeaJoernRunner,
  OWASPJavaJoernRunner,
  SecuribenchMicroJoernRunner,
  ThoratPythonJoernRunner
}
import io.joern.benchmarks.runner.semgrep.{
  IchnaeaSemgrepRunner,
  SecuribenchMicroSemgrepRunner,
  ThoratPythonSemgrepRunner
}
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
      logger.info(s"Running $benchmarkName")
      benchmarkRunner.run() match {
        case Result(Nil, _) => logger.warn(s"Empty results for $benchmarkName")
        case result =>
          formatterConstructors
            .get(config.outputFormat)
            .foreach(
              _.apply(result.copy(time = benchmarkRunner.timeSeconds))
                .writeTo(
                  config.outputDir / benchmarkName.replace(' ', '_') createDirectoryIfNotExists (createParents = true)
                )
            )
      }
    }

    if (config.benchmark == AvailableBenchmarks.ALL) {
      benchmarkConstructors.values.foreach(runBenchmark)
    } else {
      benchmarkConstructors.get(config.benchmark).foreach(runBenchmark)
    }
  }

}

object Benchmark {

  val benchmarkConstructors: Map[AvailableBenchmarks.Value, BenchmarkConfig => BenchmarkRunner] = Map(
    (AvailableBenchmarks.OWASP_JAVASRC, x => new OWASPJavaJoernRunner(x.datasetDir, JavaSrcCpgCreator())),
    (AvailableBenchmarks.OWASP_JAVA, x => new OWASPJavaJoernRunner(x.datasetDir, JVMBytecodeCpgCreator())),
    (
      AvailableBenchmarks.SECURIBENCH_MICRO_JAVASRC,
      x => new SecuribenchMicroJoernRunner(x.datasetDir, JavaSrcCpgCreator())
    ),
    (
      AvailableBenchmarks.SECURIBENCH_MICRO_JAVA,
      x => new SecuribenchMicroJoernRunner(x.datasetDir, JVMBytecodeCpgCreator())
    ),
    (AvailableBenchmarks.ICHNAEA_JSSRC, x => new IchnaeaJoernRunner(x.datasetDir, JsSrcCpgCreator())),
    (AvailableBenchmarks.THORAT_PYSRC, x => new ThoratPythonJoernRunner(x.datasetDir, PySrcCpgCreator())),
    (AvailableBenchmarks.SECURIBENCH_MICRO_SEMGREP, x => new SecuribenchMicroSemgrepRunner(x.datasetDir)),
    (AvailableBenchmarks.THORAT_SEMGREP, x => new ThoratPythonSemgrepRunner(x.datasetDir)),
    (AvailableBenchmarks.ICHNAEA_SEMGREP, x => new IchnaeaSemgrepRunner(x.datasetDir))
  )

}
