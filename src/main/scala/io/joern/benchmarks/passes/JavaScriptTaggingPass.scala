package io.joern.benchmarks.passes

import io.joern.benchmarks.*
import io.joern.benchmarks.runner.BenchmarkSourcesAndSinks
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.passes.CpgPass
import io.shiftleft.semanticcpg.language.*
import overflowdb.BatchedUpdate

class JavaScriptTaggingPass(cpg: Cpg, sourcesAndSinks: BenchmarkSourcesAndSinks)(implicit
  resolver: ICallResolver = NoResolve
) extends CpgPass(cpg)
    with TaggingPass(sourcesAndSinks) {

  override def defaultSinks: Iterator[CfgNode] = {
    cpg.call
      .nameExact("exec", "execSync", "execFileSync", "eval")
      .argument
  }

  override def defaultSanitizers: Iterator[CfgNode] = {
    cpg.method.nameExact("quote").parameter.argument
  }

}
