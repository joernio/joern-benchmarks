package io.joern.benchmarks.runner.joern

import io.joern.benchmarks.runner.{BenchmarkRunner, FindingInfo}
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Finding

/** Adds the ability to set and access a CPG during the benchmarks
  */
trait CpgBenchmarkRunner { this: BenchmarkRunner =>

  private var cpgOpt: Option[Cpg] = None

  protected def setCpg(cpg: Cpg): Unit = {
    cpgOpt = Option(cpg)
  }

  protected def cpg: Cpg = cpgOpt match {
    case Some(cpg) => cpg
    case None      => throw new RuntimeException("No CPG has been set!")
  }

  protected def mapToFindingInfo(finding: Finding): FindingInfo = {
    FindingInfo()
  }

}
