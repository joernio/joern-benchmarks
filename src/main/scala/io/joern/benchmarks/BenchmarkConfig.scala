package io.joern.benchmarks

import better.files.File

case class BenchmarkConfig(
  benchmark: AvailableBenchmarks.Value = AvailableBenchmarks.ALL,
  datasetDir: File = File("workspace"),
  outputDir: File = File("results"),
  outputFormat: OutputFormat.Value = OutputFormat.JSON
)

object AvailableBenchmarks extends Enumeration {
  val ALL               = Value
  val OWASP_JAVA        = Value
  val SECURIBENCH_MICRO = Value
}

object OutputFormat extends Enumeration {
  val JSON = Value
  val CSV  = Value
  val MD   = Value
}
