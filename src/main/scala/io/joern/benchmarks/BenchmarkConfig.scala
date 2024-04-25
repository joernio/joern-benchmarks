package io.joern.benchmarks

import better.files.File

case class BenchmarkConfig(
                            benchmark: AvailableBenchmarks.Value = AvailableBenchmarks.ALL,
                            datasetDir: File = File("workspace"),
                            outputFile: Option[File] = None,
                            outputFormat: OutputFormat.Value = OutputFormat.Markdown
                          )

object AvailableBenchmarks extends Enumeration {
  val ALL = Value
  val OWASP_JAVA_1_2 = Value
}

object OutputFormat extends Enumeration {
  val Markdown = Value
}