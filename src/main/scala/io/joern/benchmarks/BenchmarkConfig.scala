package io.joern.benchmarks

import better.files.File

case class BenchmarkConfig(
  benchmark: AvailableBenchmarks.Value = AvailableBenchmarks.ALL,
  datasetDir: File = File("workspace"),
  outputDir: File = File("results"),
  outputFormat: OutputFormat.Value = OutputFormat.JSON
)

object AvailableBenchmarks extends Enumeration {
  val ALL = Value

  // Joern
  val OWASP_JAVASRC             = Value
  val OWASP_JAVA                = Value
  val SECURIBENCH_MICRO_JAVASRC = Value
  val SECURIBENCH_MICRO_JAVA    = Value
  val ICHNAEA_JSSRC             = Value
  val THORAT_PYSRC              = Value

  // SemGrep
  val SECURIBENCH_MICRO_SEMGREP = Value
  val THORAT_SEMGREP            = Value
}

object OutputFormat extends Enumeration {
  val JSON = Value
  val CSV  = Value
  val MD   = Value
}
