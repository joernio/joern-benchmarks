package io.joern.benchmarks.runner

import better.files.File
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

import java.net.{HttpURLConnection, URL}
import scala.util.{Failure, Success, Try, Using}

sealed trait FileDownloader { this: BenchmarkRunner =>

  import FileDownloader.*

  /** Downloads the archive an unpacks it to the `benchmarkBaseDir`.
    *
    * @return
    *   `benchmarkBaseDir` if the operation was successful. A failure if otherwise.
    */
  protected def downloadBenchmarkAndUnarchive(compressionType: CompressionTypes.Value): Try[File]

  /** Downloads the archive.
    * @param ext
    *   and optional extension for the downloaded file. Must include the dot.
    * @return
    *   The downloaded file if the operation was successful. A failure if otherwise.
    */
  protected def downloadBenchmark(ext: Option[String] = None): Try[File]

  protected def downloadFile(url: URL, destFile: File): Try[File] = Try {
    if (destFile.notExists) {
      destFile.parent.createDirectoryIfNotExists(true)
      var connection: Option[HttpURLConnection] = None
      try {
        connection = Option(url.openConnection().asInstanceOf[HttpURLConnection])
        connection.foreach {
          case conn if conn.getResponseCode == HttpURLConnection.HTTP_OK =>
            Using.resources(conn.getInputStream, destFile.newFileOutputStream()) { (is, fos) =>
              val buffer = new Array[Byte](4096)
              Iterator
                .continually(is.read(buffer))
                .takeWhile(_ != -1)
                .foreach(bytesRead => fos.write(buffer, 0, bytesRead))
            }
          case conn =>
            throw new RuntimeException(
              s"Unable to download $benchmarkName from $url. Status code ${conn.getResponseCode}"
            )
        }
      } finally {
        connection.foreach(_.disconnect())
      }
    }
    destFile
  }

  protected def downloadFileAndUnarchive(url: URL, destFile: File, compressionType: CompressionTypes.Value): Unit = {
    compressionType match {
      case CompressionTypes.ZIP =>
        downloadFile(url, File(s"${destFile.pathAsString}.zip")) match {
          case Success(f) =>
            f.unzipTo(destFile)
            f.delete(swallowIOExceptions = true)
          case Failure(e) => throw e
        }
      case CompressionTypes.TGZ =>
        downloadFile(url, File(s"${destFile.pathAsString}.tgz")) match {
          case Success(f) =>
            val tarball = f.unGzipTo(File(s"${destFile.pathAsString}.tar"))
            tarball.unTarTo(destFile)
            f.delete(swallowIOExceptions = true)
            tarball.delete(swallowIOExceptions = true)
          case Failure(e) => throw e
        }
    }
  }
}

trait SingleFileDownloader extends FileDownloader { this: BenchmarkRunner =>

  /** The URL to the archive.
    */
  protected val benchmarkUrl: URL

  /** The name of the benchmark archive file name without extension.
    */
  protected val benchmarkFileName: String

  /** The name of the benchmark directory.
    */
  protected val benchmarkBaseDir: File

  override def downloadBenchmarkAndUnarchive(compressionType: CompressionTypes.Value): Try[File] = Try {
    downloadFileAndUnarchive(benchmarkUrl, datasetDir, compressionType)
    benchmarkBaseDir
  }

  override def downloadBenchmark(ext: Option[String] = None): Try[File] = Try {
    val targetFile = datasetDir / s"$benchmarkFileName${ext.getOrElse("")}"
    if (!benchmarkBaseDir.exists || benchmarkBaseDir.list.forall(_.isDirectory)) {
      benchmarkBaseDir.createDirectoryIfNotExists(createParents = true)
      downloadFile(benchmarkUrl, targetFile) match {
        case Failure(exception) => throw exception
        case _                  =>
      }
    }
    targetFile
  }

}

trait MultiFileDownloader extends FileDownloader { this: BenchmarkRunner =>

  /** The URL to the archive.
    */
  protected val benchmarkUrls: Map[String, URL]

  /** The name of the benchmark directory to download all benchmark components to.
    */
  protected val benchmarkDirName: String

  /** The name of the benchmark directory.
    */
  protected val benchmarkBaseDir: File

  override def downloadBenchmarkAndUnarchive(compressionType: CompressionTypes.Value): Try[File] = Try {
    benchmarkUrls.foreach { case (fileName, url) =>
      val targetDir =
        // TODO: Temporary fix until all the benchmarks are being downloaded from the datasets repo
        if fileName == "ichnaea" then benchmarkBaseDir
        else benchmarkBaseDir / fileName
      // TODO: Make sure dir goes to `benchmarkBaseDir / benchmarkDirName / fileName`
      if (!targetDir.isDirectory) {
        downloadFileAndUnarchive(url, targetDir, compressionType)
      }
    }

    benchmarkBaseDir
  }

  /** Downloads the archive an unpacks it to the `benchmarkBaseDir`.
    *
    * @param ext
    *   and optional extension for the downloaded file. Must include the dot.
    * @return
    *   The downloaded file if the operation was successful. A failure if otherwise.
    */
  override def downloadBenchmark(ext: Option[String] = None): Try[File] = Try {
    val targetDir = datasetDir / benchmarkDirName
    benchmarkUrls.foreach { case (fileName, url) =>
      val targetFile = targetDir / s"$fileName${ext.getOrElse("")}"
      if (!benchmarkBaseDir.exists || benchmarkBaseDir.list.forall(_.isDirectory)) {
        benchmarkBaseDir.createDirectoryIfNotExists(createParents = true)
        downloadFile(url, targetFile)
      }
    }
    targetDir
  }

}

/** The supported compression types.
  */
object CompressionTypes extends Enumeration {
  val ZIP, TGZ = Value
}

object FileDownloader {

  implicit class FileExt(targetFile: File) {

    /** Ungzips and untars the file to the given target directory.
      *
      * @param destination
      *   the directory to unpack to.
      */
    def unTarTo(destination: File): destination.type = {
      Using.resource(targetFile.newInputStream) { archiveFis =>
        val tarIs = new TarArchiveInputStream(archiveFis)
        Iterator
          .continually(tarIs.getNextEntry)
          .takeWhile(_ != null)
          .filter(sourceEntry => !sourceEntry.isDirectory && !sourceEntry.getName.contains("..") // naive zip slip check
          )
          .foreach { sourceEntry =>
            val destFile = destination / sourceEntry.getName createIfNotExists (createParents = true)
            Using.resource(destFile.newFileOutputStream()) { fos =>
              val buffer = new Array[Byte](4096)
              Iterator
                .continually(tarIs.read(buffer))
                .takeWhile(_ != -1)
                .foreach(bytesRead => fos.write(buffer, 0, bytesRead))
            }
          }
      }
      destination
    }
  }
}
