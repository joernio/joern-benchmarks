package io.joern.benchmarks

import better.files.File

case class BenchmarkConfig(
  benchmark: AvailableBenchmarks.Value = AvailableBenchmarks.SECURIBENCH_MICRO,
  frontend: AvailableFrontends.Value = AvailableFrontends.JAVASRC,
  datasetDir: File = File("workspace"),
  outputDir: File = File("results"),
  outputFormat: OutputFormat.Value = OutputFormat.MD,
  disableSemantics: Boolean = false,
  maxCallDepth: Int = 4,
  iterations: Int = 1
)

object AvailableBenchmarks extends Enumeration {
  val SECURIBENCH_MICRO = Value
  val ICHNAEA           = Value
  val THORAT            = Value
}

object AvailableFrontends extends Enumeration {
  val JAVASRC = Value
  val JAVA    = Value
  val JSSRC   = Value
  val PYSRC   = Value
  val SEMGREP = Value
  val CODEQL  = Value
}

object OutputFormat extends Enumeration {
  val JSON = Value
  val CSV  = Value
  val MD   = Value
}
