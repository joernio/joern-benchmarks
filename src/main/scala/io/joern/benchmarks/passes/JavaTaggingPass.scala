package io.joern.benchmarks.passes

import io.joern.benchmarks.runner.BenchmarkSourcesAndSinks
import io.joern.x2cpg.Defines
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.codepropertygraph.generated.{Cpg, EdgeTypes}
import io.shiftleft.passes.CpgPass
import io.shiftleft.semanticcpg.language.*

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
    (cpg.method
      .filter(_.fullName.startsWith("java.io.File"))
      .nameExact(Defines.ConstructorMethodName, "write") ++
      cpg.method
        .filter(_.fullName.startsWith("java.io.PrintWriter"))
        .nameExact("print", "println") ++
      cpg.method
        .filter(_.fullName.startsWith("java.sql.Connection"))
        .nameExact("prepareStatement") ++
      cpg.method
        .filter(_.fullName.startsWith("java.sql.Statement"))
        .nameExact("execute", "executeUpdate", "executeQuery") ++
      cpg.method
        .filter(_.fullName.startsWith("javax.servlet.http.HttpServletResponse"))
        .nameExact("sendRedirect") ++
      cpg.method
        .filter(_.fullName.startsWith("java.io.FileInputStream"))
        .nameExact(Defines.ConstructorMethodName) ++
      cpg.method
        .filter(_.fullName.startsWith("java.io.FileWriter"))
        .nameExact(Defines.ConstructorMethodName)).parameter.argument
      .where(_.argumentIndex(1)) ++ cpg.method
      .filter(_.fullName.startsWith("java.io.File"))
      .nameExact("createNewFile")
      .callIn
  }

}
