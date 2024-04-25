package io.joern.benchmarks

import io.joern.benchmarks.Benchmark.benchmarkConstructors
import io.joern.benchmarks.runner.{BenchmarkRunner, OWASPJavaV1_2Runner}
import org.slf4j.LoggerFactory

/** The main benchmarking process.
  */
class Benchmark(config: BenchmarkConfig) {

  private val logger = LoggerFactory.getLogger(getClass)

  /** The benchmarking entrypoint.
    */
  def evaluate(): Unit = {
    logger.info("Beginning evaluation")
    if (config.benchmark == AvailableBenchmarks.ALL) {
      benchmarkConstructors.values.foreach(_.apply(config).run())
    } else {
      benchmarkConstructors.get(config.benchmark).foreach(_.apply(config).run())
    }
  }

}

object Benchmark {

  val benchmarkConstructors: Map[AvailableBenchmarks.Value, BenchmarkConfig => BenchmarkRunner] = Map(
    (AvailableBenchmarks.OWASP_JAVA_1_2, x => new OWASPJavaV1_2Runner(x.datasetDir))
  )

}
