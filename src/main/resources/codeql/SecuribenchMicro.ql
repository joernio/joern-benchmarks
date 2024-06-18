import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking

/**
 * @name Tainted data in `HttpServletRequest` written to `PrintWriter`
 * @description Track tainted data from `HttpServletRequest` to `PrintWriter.println`
 * @kind path-problem
 * @id java/http-servlet-println
 * @problem.severity error
 * @tags security
 */
module HttpServletRequestToPrintWriterConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(MethodCall call, Parameter param |
      call.getAnEnclosingStmt() = param.getAnAccess().getAnEnclosingStmt() and
      source.asExpr() = call and
      call.getReceiverType().hasName("HttpServletRequest") and
      call.getMethod().hasName("getParameter")
    )
    or
    exists(MethodCall servletConfig |
      source.asExpr() = servletConfig and
      servletConfig.getCallee().hasName("getServletConfig")
    )
    or
    exists(MethodCall getCookies |
      source.asExpr() = getCookies and
      getCookies.getCallee().hasName("getCookies")
    )
  }

  predicate isSink(DataFlow::Node sink) {
    exists(MethodCall printCall |
      sink.asExpr() = printCall.getArgument(0) and
      printCall.getReceiverType().hasQualifiedName("java.io", "PrintWriter") and
      (printCall.getCallee().hasName("println") or printCall.getCallee().hasName("print"))
    )
    or
    exists(MethodCall writeCall |
      sink.asExpr() = writeCall.getArgument(0) and
      writeCall.getReceiverType().hasName("File") and
      writeCall.getCallee().hasName("write")
    )
    or
    exists(ConstructorCall fileConstr |
      sink.asExpr() = fileConstr.getArgument(0) and
      (
        fileConstr.getConstructedType().hasName("File") or
        fileConstr.getConstructedType().hasName("FileWriter") or
        fileConstr.getConstructedType().hasName("FileInputStream")
      )
    )
    or
    exists(MethodCall createFile |
      sink.asExpr() = createFile and
      createFile.getReceiverType().hasQualifiedName("java.io", "File") and
      createFile.getCallee().hasName("createNewFile")
    )
    or
    exists(MethodCall sqlCall |
      sink.asExpr() = sqlCall.getArgument(0) and
      sqlCall.getReceiverType().hasName("Statement") and
      (
        sqlCall.getCallee().hasName("execute") or
        sqlCall.getCallee().hasName("executeUpdate") or
        sqlCall.getCallee().hasName("executeQuery")
      )
    )
    or
    exists(MethodCall badPrepStmt |
      sink.asExpr() = badPrepStmt.getArgument(0) and
      badPrepStmt.getReceiverType().hasName("Connection") and
      badPrepStmt.getCallee().hasName("prepareStatement")
    )
    or
    exists(MethodCall sendRedirect |
      sink.asExpr() = sendRedirect.getArgument(0) and
      sendRedirect.getReceiverType().hasName("HttpServletResponse") and
      sendRedirect.getCallee().hasName("sendRedirect")
    )
  }
}

module HttpServletRequestToPrintWriterFlow =
  TaintTracking::Global<HttpServletRequestToPrintWriterConfig>;

import HttpServletRequestToPrintWriterFlow::PathGraph

from
  HttpServletRequestToPrintWriterFlow::PathNode source,
  HttpServletRequestToPrintWriterFlow::PathNode sink
where HttpServletRequestToPrintWriterFlow::flowPath(source, sink)
select sink.getNode(), source, sink, "Properties from this $@ are written a sensitive sink.",
  source.getNode(), "attacker-controlled HTTP request"
