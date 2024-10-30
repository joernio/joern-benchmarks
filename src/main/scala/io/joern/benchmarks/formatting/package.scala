package io.joern.benchmarks

import io.joern.benchmarks.OutputFormat
import io.joern.benchmarks.Domain.*
import better.files.File
import better.files.File.OpenOptions

package object formatting {

  sealed trait ResultFormatter {

    /** Exports the results to the output directory using the format of the implementing class.
      * @param outputDir
      *   the directory to write results to.
      */
    def writeTo(outputDir: File): Unit
  }

  private class JsonFormatter(result: BaseResult) extends ResultFormatter {
    override def writeTo(outputDir: File): Unit = {
      result match {
        case perf: PerformanceTestResult =>
          (outputDir / "perf_results.json").fileOutputStream().apply { fos =>
            upickle.default.writeToOutputStream(perf, fos, indent = 2)
          }
        case taintRes: TaintAnalysisResult =>
          (outputDir / "taint_results.json").fileOutputStream().apply { fos =>
            upickle.default.writeToOutputStream(taintRes, fos, indent = 2)
          }
      }

    }
  }

  private class CsvFormatter(result: BaseResult) extends ResultFormatter {
    override def writeTo(outputDir: File): Unit = {
      result match {
        case result @ PerformanceTestResult(entries, k) =>
          (outputDir / "perf_results.csv").bufferedWriter(openOptions = OpenOptions.append).apply { bw =>
            bw.write("test_name,k,time\n")
            entries.sortBy(_.name).foreach { case PerfRun(testName, time) =>
              bw.write(f"$testName,$k,${String.format(java.util.Locale.US, "%.4f", time)}\n")
            }
          }
        case result @ TaintAnalysisResult(entries, times) =>
          (outputDir / "test_results.csv").bufferedWriter.apply { bw =>
            bw.write("test_name,outcome\n")
            entries.sortBy(_.testName).foreach { case TestEntry(testName, outcome) =>
              bw.write(s"$testName,$outcome\n")
            }
          }
          (outputDir / "metrics.csv").bufferedWriter.apply { bw =>
            bw.write("name,value\n")
            bw.write(f"mean_time (s),${result.meanTime}%1.2f\n")
            bw.write(f"stderr_time (s),${result.stderrTime}%1.2f\n")
            bw.write(s"iterations,${times.size}\n")
            bw.write(f"j_index,${result.jIndex}%1.3f\n")
            bw.write(f"f_score,${result.fscore}%1.3f\n")
            bw.write(s"true_positive,${result.tp}\n")
            bw.write(s"false_positive,${result.fp}\n")
            bw.write(s"true_negative,${result.tn}\n")
            bw.write(s"false_negative,${result.fn}\n")
          }
      }

    }
  }

  private class MarkdownFormatter(result: BaseResult) extends ResultFormatter {
    override def writeTo(outputDir: File): Unit = {
      result match {
        case PerformanceTestResult(entries, k) =>
          (outputDir / "perf_results.md").bufferedWriter.apply { fos =>
            fos.write("# Statistics\n\n")
            fos.write("\n# Test Outcomes \n\n")
            fos.write(f"Max call depth (k): $k  \n")
            fos.write("|Test Name|Outcome|\n")
            fos.write("|---|---|\n")
            entries.sortBy(_.name).foreach { case PerfRun(testName, time) =>
              fos.write(s"|$testName|${String.format(java.util.Locale.US, "%.4f", time)}|\n")
            }
          }
        case result @ TaintAnalysisResult(entries, times) =>
          (outputDir / "taint_results.md").bufferedWriter.apply { fos =>
            fos.write("# Statistics\n\n")
            fos.write(f"Avg. Time (s): ${result.meanTime}%1.2f  \n")
            fos.write(f"StdErr Time (s): ${result.stderrTime}%1.2f  \n")
            fos.write(s"Iterations: ${times.size}  \n")
            fos.write(f"J Index: ${result.jIndex}%1.3f  \n")
            fos.write(f"F Score: ${result.fscore}%1.3f  \n")
            fos.write(s"TP: ${result.tp} | FP: ${result.fp}  \n")
            fos.write(s"TN: ${result.tn} | FN: ${result.fn}  \n")
            fos.write("\n# Test Outcomes \n\n")
            fos.write("|Test Name|Outcome|\n")
            fos.write("|---|---|\n")
            entries.sortBy(_.testName).foreach { case TestEntry(testName, outcome) =>
              fos.write(s"|$testName|$outcome|\n")
            }
          }
      }

    }
  }

  val formatterConstructors: Map[OutputFormat.Value, BaseResult => ResultFormatter] = Map(
    (OutputFormat.JSON, x => new JsonFormatter(x)),
    (OutputFormat.CSV, x => new CsvFormatter(x)),
    (OutputFormat.MD, x => new MarkdownFormatter(x))
  )

}
