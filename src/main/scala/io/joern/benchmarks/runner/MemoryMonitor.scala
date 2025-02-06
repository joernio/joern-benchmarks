package io.joern.benchmarks.runner

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import java.lang.management.ManagementFactory
import scala.sys.process.*
import scala.collection.mutable.ArrayBuffer

/** A utility for measuring the memory usage of a process using the `ps` command.
  */
object MemoryMonitor {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val measuring = java.util.concurrent.atomic.AtomicBoolean(false)
  private val logger    = LoggerFactory.getLogger(getClass)

  private def getProcessMemoryUsage(pid: Long): Option[Long] = {
    try {
      val output = Seq("ps", "-o", "rss=", "-p", pid.toString).!!.trim
      output.toLongOption.map(_ * 1024) // Convert from KB to bytes
    } catch {
      case x: Throwable =>
        logger.error("Unable to measure memory at current epoch", x)
        None
    }
  }

  /** Stops the measuring process, allowing the last `Future` to terminate.
    */
  def stopMeasuring(): Unit = measuring.set(false)

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
        getProcessMemoryUsage(pid).foreach(memoryUsageData.append)
        Thread.sleep(100) // Sleep for 0.1 second
      }
      memoryUsageData
    }
  }

  def getCurrentProcessId: Long = {
    val name = ManagementFactory.getRuntimeMXBean.getName
    name.split('@').head.toLong
  }
}
