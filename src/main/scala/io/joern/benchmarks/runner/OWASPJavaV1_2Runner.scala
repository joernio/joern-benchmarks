package io.joern.benchmarks.runner

import better.files.File
import io.shiftleft.codepropertygraph.generated.nodes.Finding
import org.slf4j.LoggerFactory

import java.io.FileOutputStream
import java.net.{HttpURLConnection, URI}
import scala.util.{Failure, Success, Try, Using}

class OWASPJavaV1_2Runner(datasetDir: File) extends BenchmarkRunner(datasetDir) {

  private val logger = LoggerFactory.getLogger(getClass)
  
  protected val benchmarkUrl = URI(
    "https://github.com/OWASP-Benchmark/BenchmarkJava/archive/refs/tags/1.2beta.zip"
  ).toURL

  override def initialize(): Try[File] = Try {
    val targetDir = datasetDir / "owasp-java-1_2"
    if (!targetDir.exists || targetDir.list.filterNot(_.isDirectory).isEmpty) {
      var connection: Option[HttpURLConnection] = None
      val targetFile = datasetDir / "owasp-java-1_2.zip"
      try {
        connection = Option(benchmarkUrl.openConnection().asInstanceOf[HttpURLConnection])
        connection.foreach {
          case conn if conn.getResponseCode == HttpURLConnection.HTTP_OK =>
            Using.resources(conn.getInputStream, new FileOutputStream(datasetDir.pathAsString)) { (is, fos) =>
              val buffer = new Array[Byte](4096)
              Iterator
                .continually(is.read(buffer))
                .takeWhile(_ != -1)
                .foreach(bytesRead => fos.write(buffer, 0, bytesRead))
            }
            targetFile.unzipTo(datasetDir)
          case conn =>
            throw new RuntimeException(
              s"Unable to download OWASP Java 1.2 from $benchmarkUrl. Status code ${conn.getResponseCode}"
            )
        }
      } finally {
        connection.foreach(_.disconnect())
        targetFile.delete(swallowIOExceptions = true)
      }
    }
    targetDir
  }

  override def findings(testInput: File): List[Finding] = {
    Nil
  }

  override def compare(testName: String, findings: List[Finding]): TestOutcome.Value = {
    TestOutcome.TN
  }

  override def run(): Result = {
    initialize() match {
      case Failure(exception) =>
        logger.error(s"Unable to initialize benchmark '$getClass'", exception)
        Result()
      case Success(benchmarkDir) =>
        Result()
    }
  }
  
}
