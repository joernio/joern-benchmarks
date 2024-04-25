package io.joern.benchmarks

import io.joern.benchmarks.Benchmark.benchmarkConstructors
import io.joern.benchmarks.runner.{BenchmarkRunner, OWASPJavaV1_2Runner, Result, TestOutcome}
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
      logger.info(s"Running ${benchmarkRunner.benchmarkName}")
      benchmarkRunner.run() match {
        case Result(Nil) => logger.warn(s"Empty results for ${benchmarkRunner.benchmarkName}")
        case x =>
          println(write(x, indent = 2))
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
    (AvailableBenchmarks.OWASP_JAVA_1_2, x => new OWASPJavaV1_2Runner(x.datasetDir))
  )

}
