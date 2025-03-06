package io.joern.benchmarks.runner

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import java.lang.management.ManagementFactory
import scala.sys.process.*
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/** A utility for measuring the memory usage of a process using the `ps` command.
  */
object MemoryMonitor {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val measuring = java.util.concurrent.atomic.AtomicBoolean(false)
  private val logger    = LoggerFactory.getLogger(getClass)

  private def getProcessMemoryUsage(pid: Long): Option[Long] = {
    def getMemoryForPid(pid: Long): Option[Long] = {
      try {
        val output = Seq("ps", "-o", "rss=", "-p", pid.toString).!!.trim
        output.toLongOption.map(_ * 1024) // Convert KB to bytes
      } catch {
        case x: Throwable =>
          logger.error("Unable to measure memory at current epoch", x)
          None
      }
    }

    def getChildPids(parentPid: Long): Seq[Long] = {
      try {
        val output = Seq("pgrep", "-P", parentPid.toString).!!.trim
        output.split("\\s+").flatMap(_.toLongOption)
      } catch {
        case x: RuntimeException if x.getMessage.contains("Nonzero exit value: 1") =>
          logger.debug("Unable to measure memory for child processes at current epoch, likely no child processes exist")
          Seq.empty
        case x: RuntimeException =>
          logger.error("Unable to measure memory for child processes at current epoch", x)
          Seq.empty
      }
    }

    getMemoryForPid(pid) match {
      case None         => None
      case Some(pidMem) => Option(pidMem + getChildPids(pid).flatMap(getMemoryForPid).sum)
    }
  }

  /** Stops the measuring process, allowing the last `Future` to terminate.
    */
  def stopMeasuring(): Unit = measuring.set(false)

  /** Determines if the given process is still alive.
    * @param pid
    *   the process ID.
    * @return
    *   true if alive, false if otherwise.
    */
  private def isProcessAlive(pid: Long): Boolean = {
    Try {
      val output = Seq("ps", "-p", pid.toString).!!.trim
      output.linesIterator.drop(1).hasNext // Check if there's output beyond the header
    }.getOrElse(false)
  }

  /** Begins measuring the memory of the process given by `pid`. Must use `stopMeasuring` to terminate the measuring
    * thread.
    * @param pid
    *   the process to measure
    * @return
    *   a future holding the thread measuring memory.
    */
  def monitorMemoryUsage(pid: Long): Future[ArrayBuffer[Long]] = {
    val memoryUsageData = ArrayBuffer[Long]()
    measuring.set(true)

    Future {
      while (measuring.get()) {
        if (isProcessAlive(pid)) {
          getProcessMemoryUsage(pid).foreach(memoryUsageData.append)
          Thread.sleep(100) // Sleep for 0.1 second
        } else {
          stopMeasuring()
        }
      }
      memoryUsageData
    }
  }

  def getCurrentProcessId: Long = {
    val name = ManagementFactory.getRuntimeMXBean.getName
    name.split('@').head.toLong
  }
}
