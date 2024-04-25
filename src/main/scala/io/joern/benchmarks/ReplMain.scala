package io.joern.benchmarks

import io.joern.console.BridgeBase
import io.joern.joerncli.console.Predefined

/** Extend/use joern as a REPL application */
object ReplMain extends BridgeBase {

  def main(args: Array[String]): Unit = {
    run(parseConfig(args))
  }

  override protected def predefLines = {
    Predefined.forInteractiveShell ++ Seq(
      s"import _root_.${getClass.getPackageName}.*"
    )
  }

  override protected def promptStr = "benchmark-repl"
  override protected def greeting = "Welcome to the benchmark REPL!"
  override protected def onExitCode = """println("goodbye!")"""
  override def applicationName = "benchmarks-dataflowengineoss"
}
