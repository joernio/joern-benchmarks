package io.joern.benchmarks.passes

import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.joern.benchmarks.runner.BenchmarkSourcesAndSinks
import io.shiftleft.passes.CpgPass
import io.joern.benchmarks.*
import overflowdb.BatchedUpdate

trait TaggingPass(sourcesAndSinks: BenchmarkSourcesAndSinks) { this: CpgPass =>

  override def run(builder: DiffGraphBuilder): Unit = {
    implicit val dgb: DiffGraphBuilder = builder
    (sourcesAndSinks.sources ++ defaultSources).tagAsSource
    (sourcesAndSinks.sinks ++ defaultSinks).tagAsSink
    (sourcesAndSinks.sanitizers ++ defaultSanitizers).tagAsSanitizer
  }

  protected def defaultSources: Iterator[CfgNode] = Iterator.empty

  protected def defaultSinks: Iterator[CfgNode] = Iterator.empty

  protected def defaultSanitizers: Iterator[CfgNode] = Iterator.empty

}
