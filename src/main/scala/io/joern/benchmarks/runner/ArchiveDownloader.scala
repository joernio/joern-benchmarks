package io.joern.benchmarks.runner

import java.net.{HttpURLConnection, URL}
import better.files.File

import java.io.FileOutputStream
import scala.util.{Try, Using}

trait ArchiveDownloader { this: BenchmarkRunner =>

  /** The URL to the archive.
    */
  protected val benchmarkUrl: URL

  /** The name of the benchmark archive file name without extension.
    */
  protected val benchmarkFileName: String

  /** The name of the benchmark directory.
    */
  protected val benchmarkBaseDir: File

  /** Downloads the archive an unpacks it to the `benchmarkBaseDir`. Note: Only ZIP archives are currently supported.
    * @return
    *   `benchmarkBaseDir` if the operation was successful. A failure if otherwise.
    */
  def downloadBenchmark: Try[File] = Try {
    if (!benchmarkBaseDir.exists || benchmarkBaseDir.list.forall(_.isDirectory)) {
      benchmarkBaseDir.createDirectoryIfNotExists(createParents = true)
      var connection: Option[HttpURLConnection] = None
      val targetFile                            = datasetDir / s"$benchmarkFileName.zip"
      try {
        connection = Option(benchmarkUrl.openConnection().asInstanceOf[HttpURLConnection])
        connection.foreach {
          case conn if conn.getResponseCode == HttpURLConnection.HTTP_OK =>
            Using.resources(conn.getInputStream, new FileOutputStream(targetFile.pathAsString)) { (is, fos) =>
              val buffer = new Array[Byte](4096)
              Iterator
                .continually(is.read(buffer))
                .takeWhile(_ != -1)
                .foreach(bytesRead => fos.write(buffer, 0, bytesRead))
            }
            targetFile.unzipTo(datasetDir)
          case conn =>
            throw new RuntimeException(
              s"Unable to download $benchmarkName from $benchmarkUrl. Status code ${conn.getResponseCode}"
            )
        }
      } finally {
        connection.foreach(_.disconnect())
        targetFile.delete(swallowIOExceptions = true)
      }
    }
    benchmarkBaseDir
  }

}
