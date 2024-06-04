package io.joern.benchmarks.passes

import io.joern.benchmarks.runner.BenchmarkSourcesAndSinks
import io.joern.x2cpg.Defines
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.codepropertygraph.generated.{Cpg, EdgeTypes}
import io.shiftleft.passes.CpgPass
import io.shiftleft.semanticcpg.language.*
import overflowdb.BatchedUpdate

class JavaTaggingPass(cpg: Cpg, sourcesAndSinks: BenchmarkSourcesAndSinks)(implicit resolver: ICallResolver = NoResolve)
    extends CpgPass(cpg)
    with TaggingPass(sourcesAndSinks) {

  override def defaultSources: Iterator[CfgNode] = {
    cpg.typeDecl
      .fullNameExact("javax.servlet.http.HttpServletRequest")
      .referencingType
      .flatMap(_.evalTypeIn)
      .isParameter ++
      cpg.typeDecl
        .fullNameExact("javax.servlet.http.Cookie")
        .method
        .nameExact("getValue")
        .methodReturn ++
      cpg.method.nameExact("getServletConfig").methodReturn
  }

  override def defaultSinks: Iterator[CfgNode] = {
    cpg.method
      .filter(_.fullName.startsWith("java.io.File"))
      .nameExact(Defines.ConstructorMethodName, "write", "createNewFile")
      .parameter
      .argument ++
      cpg.method
        .filter(_.fullName.startsWith("java.io.PrintWriter"))
        .nameExact("print", "println")
        .parameter
        .argument ++
      cpg.method
        .filter(_.fullName.startsWith("java.sql.Connection"))
        .nameExact("prepareStatement")
        .parameter
        .argument
        .where(_.argumentIndex(1)) ++
      cpg.method
        .filter(_.fullName.startsWith("java.sql.Statement"))
        .nameExact("execute", "executeUpdate", "executeQuery")
        .parameter
        .argument
        .where(_.argumentIndex(1)) ++
      cpg.method
        .filter(_.fullName.startsWith("javax.servlet.http.HttpServletResponse.sendRedirect"))
        .parameter
        .argument
        .where(_.argumentIndex(1))
  }

}
