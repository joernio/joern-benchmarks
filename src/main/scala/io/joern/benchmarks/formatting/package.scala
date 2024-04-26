package io.joern.benchmarks

import io.joern.benchmarks.OutputFormat
import io.joern.benchmarks.Domain.*
import better.files.File

package object formatting {

  sealed trait ResultFormatter(result: Result) {

    /** Exports the results to the output directory using the format of the implementing class.
      * @param outputDir
      *   the directory to write results to.
      */
    def writeTo(outputDir: File): Unit
  }

  private class JsonFormatter(result: Result) extends ResultFormatter(result) {
    override def writeTo(outputDir: File): Unit = {
      (outputDir / "results.json").fileOutputStream().apply { fos =>
        upickle.default.writeToOutputStream(result, fos, indent = 2)
      }
    }
  }

  private class CsvFormatter(result: Result) extends ResultFormatter(result) {
    override def writeTo(outputDir: File): Unit = {
      (outputDir / "test_results.csv").bufferedWriter.apply { bw =>
        bw.write("test_name,outcome\n")
        result.entries.foreach { case TestEntry(testName, outcome) =>
          bw.write(s"$testName,$outcome\n")
        }
      }
      (outputDir / "metrics.csv").bufferedWriter.apply { bw =>
        bw.write("name,value\n")
        bw.write(s"youdenIndex,${result.youdenIndex}\n")
      }
    }
  }

  private class MarkdownFormatter(result: Result) extends ResultFormatter(result) {
    override def writeTo(outputDir: File): Unit = {
      (outputDir / "results.md").bufferedWriter.apply { fos =>
        fos.write("# Statistics\n\n")
        fos.write(s"Youden Index: ${result.youdenIndex}\n")
        fos.write("# Test Outcomes \n\n")
        fos.write("|Test Name|Outcome|\n")
        fos.write("|---|---|\n")
        result.entries.foreach { case TestEntry(testName, outcome) =>
          fos.write(s"|$testName|$outcome|\n")
        }
      }
    }
  }

  val formatterConstructors: Map[OutputFormat.Value, Result => ResultFormatter] = Map(
    (OutputFormat.JSON, x => new JsonFormatter(x)),
    (OutputFormat.CSV, x => new CsvFormatter(x)),
    (OutputFormat.MD, x => new MarkdownFormatter(x))
  )

}
