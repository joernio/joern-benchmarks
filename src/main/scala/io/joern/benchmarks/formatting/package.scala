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

  private def formatDouble(x: Double): String = String.format(java.util.Locale.US, "%.4f", x)

  private class CsvFormatter(result: BaseResult) extends ResultFormatter {

    override def writeTo(outputDir: File): Unit = {
      result match {
        case result @ PerformanceTestResult(entries, k) =>
          val targetFile = outputDir / "perf_results.csv"
          val exists     = targetFile.exists
          (outputDir / "perf_results.csv").bufferedWriter(openOptions = OpenOptions.append).apply { bw =>
            if !exists then bw.write("test_name,k,time,memory_avg,memory_stderr\n")
            entries.sortBy(_.name).foreach { case p @ PerfRun(testName, time, mem) =>
              val timeAvgStr   = formatDouble(time)
              val memMeanStr   = String.format(java.util.Locale.US, "%.4f", p.meanMem)
              val memStderrStr = String.format(java.util.Locale.US, "%.4f", p.stderrMem)
              bw.write(f"$testName,$k,$timeAvgStr,$memMeanStr,$memStderrStr\n")
            }
          }
        case result @ TaintAnalysisResult(entries, times, _) =>
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
            bw.write(f"mean_memory (b),${result.meanTime}%1.2f\n")
            bw.write(f"stderr_memory (b),${result.stderrMem}%1.2f\n")
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
            fos.write(f"Max call depth (k): $k  \n")
            fos.write("\n# Test Outcomes \n\n")
            fos.write("|Test Name|Outcome|Mem Avg|Mem Stderr|\n")
            fos.write("|---|---|\n")
            entries.sortBy(_.name).foreach { case p @ PerfRun(testName, time, _) =>
              val timeAvgStr   = formatDouble(time)
              val memMeanStr   = String.format(java.util.Locale.US, "%.4f", p.meanMem)
              val memStderrStr = String.format(java.util.Locale.US, "%.4f", p.stderrMem)
              fos.write(s"|$testName|$timeAvgStr|$memMeanStr|$memStderrStr|\n")
            }
          }
        case result @ TaintAnalysisResult(entries, times, _) =>
          (outputDir / "taint_results.md").bufferedWriter.apply { fos =>
            fos.write("# Statistics\n\n")
            fos.write(f"Avg. Time (s): ${result.meanTime}%1.2f  \n")
            fos.write(f"StdErr Time (s): ${result.stderrTime}%1.2f  \n")
            fos.write(f"Mean Memory (b): ${result.meanMem}%1.2f  \n")
            fos.write(f"StdErr Memory (b): ${result.stderrMem}%1.2f  \n")
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
