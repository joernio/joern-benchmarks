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
        result.entries.sortBy(_.testName).foreach { case TestEntry(testName, outcome) =>
          bw.write(s"$testName,$outcome\n")
        }
      }
      (outputDir / "metrics.csv").bufferedWriter.apply { bw =>
        bw.write("name,value\n")
        bw.write(f"time_seconds,${result.time}%1.2f\n")
        bw.write(f"j_index,${result.jIndex}%1.3f\n")
        bw.write(f"f_score,${result.fscore}%1.3f\n")
        bw.write(s"true_positive,${result.tp}\n")
        bw.write(s"false_postive,${result.fp}\n")
        bw.write(s"true_negative,${result.tn}\n")
        bw.write(s"false_negative,${result.fn}\n")
      }
    }
  }

  private class MarkdownFormatter(result: Result) extends ResultFormatter(result) {
    override def writeTo(outputDir: File): Unit = {
      (outputDir / "results.md").bufferedWriter.apply { fos =>
        fos.write("# Statistics\n\n")
        fos.write(f"Time (s): ${result.time}%1.2f\n")
        fos.write(f"J Index: ${result.jIndex}%1.3f\n")
        fos.write(f"F Score: ${result.fscore}%1.3f\n")
        fos.write(s"TP: ${result.tp} | FP: ${result.fp}\n")
        fos.write(s"TN: ${result.tn} | FN: ${result.fn}\n")
        fos.write("\n# Test Outcomes \n\n")
        fos.write("|Test Name|Outcome|\n")
        fos.write("|---|---|\n")
        result.entries.sortBy(_.testName).foreach { case TestEntry(testName, outcome) =>
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
