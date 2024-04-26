package io.joern.benchmarks

import better.files.File

case class BenchmarkConfig(
  benchmark: AvailableBenchmarks.Value = AvailableBenchmarks.ALL,
  datasetDir: File = File("workspace"),
  outputFile: Option[File] = None,
  outputFormat: OutputFormat.Value = OutputFormat.Json
)

object AvailableBenchmarks extends Enumeration {
  val ALL               = Value
  val OWASP_JAVA_1_2    = Value
  val SECURIBENCH_MICRO = Value
}

object OutputFormat extends Enumeration {
  val Json = Value
}
