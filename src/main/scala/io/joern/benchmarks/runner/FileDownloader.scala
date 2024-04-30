package io.joern.benchmarks.runner

import java.net.{HttpURLConnection, URL}
import better.files.File

import java.io.FileOutputStream
import scala.util.{Try, Success, Failure, Using}

trait FileDownloader { this: BenchmarkRunner =>

  /** The URL to the archive.
    */
  protected val benchmarkUrl: URL

  /** The name of the benchmark archive file name without extension.
    */
  protected val benchmarkFileName: String

  /** The name of the benchmark directory.
    */
  protected val benchmarkBaseDir: File

  protected def downloadBenchmarkAndUnzip: Try[File] = Try {
    downloadBenchmark(Option(".zip")) match {
      case Success(f) =>
        f.unzipTo(datasetDir)
        f.delete(swallowIOExceptions = true)
      case Failure(e) => throw e
    }
    benchmarkBaseDir
  }

  /** Downloads the archive an unpacks it to the `benchmarkBaseDir`.
    * @param ext
    *   and optional extension for the downloaded file. Must include the dot.
    * @return
    *   The downloaded file if the operation was successful. A failure if otherwise.
    */
  protected def downloadBenchmark(ext: Option[String] = None): Try[File] = Try {
    val targetFile = datasetDir / s"$benchmarkFileName${ext.getOrElse("")}"
    if (!benchmarkBaseDir.exists || benchmarkBaseDir.list.forall(_.isDirectory)) {
      benchmarkBaseDir.createDirectoryIfNotExists(createParents = true)
      downloadFile(benchmarkUrl, targetFile)
    }
    targetFile
  }

  protected def downloadFile(url: URL, destFile: File): Unit = {
    if (destFile.notExists) {
      var connection: Option[HttpURLConnection] = None
      try {
        connection = Option(benchmarkUrl.openConnection().asInstanceOf[HttpURLConnection])
        connection.foreach {
          case conn if conn.getResponseCode == HttpURLConnection.HTTP_OK =>
            Using.resources(conn.getInputStream, new FileOutputStream(destFile.pathAsString)) { (is, fos) =>
              val buffer = new Array[Byte](4096)
              Iterator
                .continually(is.read(buffer))
                .takeWhile(_ != -1)
                .foreach(bytesRead => fos.write(buffer, 0, bytesRead))
            }
          case conn =>
            throw new RuntimeException(
              s"Unable to download $benchmarkName from $benchmarkUrl. Status code ${conn.getResponseCode}"
            )
        }
      } finally {
        connection.foreach(_.disconnect())
      }
    }
  }

}
