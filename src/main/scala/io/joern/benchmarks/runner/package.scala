package io.joern.benchmarks

import java.io.{BufferedReader, File, InputStreamReader}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

package object runner {

  def runCmd(in: String, cwd: File): RunOutput = {
    val processBuilder = new ProcessBuilder(in.split(" ")*)
      .directory(cwd)
      .redirectErrorStream(true)

    val process      = processBuilder.start() // Start the process
    val pid          = process.pid()          // Capture process ID
    val memoryFuture = MemoryMonitor.monitorMemoryUsage(pid)

    val out = scala.collection.mutable.ListBuffer[String]()
    val err = scala.collection.mutable.ListBuffer[String]()

    // Read stdout & stderr
    val stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream))
    val stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream))

    var line: String = null
    while ({
      line = stdOutReader.readLine();
      line != null
    }) out += line
    while ({
      line = stdErrReader.readLine();
      line != null
    }) err += line
    val exitCode =
      try {
        process.waitFor() // Wait for the process to complete
      } finally {
        MemoryMonitor.stopMeasuring()
      }

    val memory = Await.result(memoryFuture, Duration.Inf).toList
    RunOutput(exitCode, out.toList, err.toList, memory)
  }

  case class RunOutput(exitCode: Int, stdOut: List[String], stdErr: List[String], memory: List[Long]) {
    def toTry: Try[RunOutput] = {
      exitCode match {
        case 0 => Success(this)
        case _ => Failure(new RuntimeException(stdErr.mkString("\n")))
      }
    }
  }

}
