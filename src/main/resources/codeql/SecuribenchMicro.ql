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
        call.getAChildExpr().toString() = param.getName().toString() and
        source.asExpr() = call and
        call.getReceiverType().hasName("HttpServletRequest") and
        call.getMethod().hasName("getParameter")
      )
    }

    predicate isSink(DataFlow::Node sink) {
        exists(MethodCall call |
          sink.asExpr() = call.getArgument(0) and
          call.getReceiverType().hasQualifiedName("java.io", "PrintWriter") and
          call.getCallee().getName() = "println"
        )
    }
}

module HttpServletRequestToPrintWriterFlow = TaintTracking::Global<HttpServletRequestToPrintWriterConfig>;

import HttpServletRequestToPrintWriterFlow::PathGraph

from HttpServletRequestToPrintWriterFlow::PathNode source, HttpServletRequestToPrintWriterFlow::PathNode sink
where HttpServletRequestToPrintWriterFlow::flowPath(source, sink)
select sink.getNode(), source, sink, "Properties from this $@ are written to a print stream.", source.getNode(),
  "attacker-controlled HTTP request"