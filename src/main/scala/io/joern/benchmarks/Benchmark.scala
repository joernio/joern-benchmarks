package io.joern.benchmarks

import io.joern.benchmarks.Benchmark.benchmarkConstructors
import io.joern.benchmarks.formatting.formatterConstructors
import io.joern.benchmarks.runner.{BenchmarkRunner, OWASPJavaRunner, SecuribenchMicroRunner}
import org.slf4j.LoggerFactory
import io.joern.benchmarks.Domain.*
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
        case Result(Nil) => logger.warn(s"Empty results for $benchmarkName")
        case result =>
          formatterConstructors
            .get(config.outputFormat)
            .foreach(
              _.apply(result)
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
    (AvailableBenchmarks.OWASP_JAVA, x => new OWASPJavaRunner(x.datasetDir)),
    (AvailableBenchmarks.SECURIBENCH_MICRO, x => new SecuribenchMicroRunner(x.datasetDir))
  )

}
