import javascript
import semmle.javascript.dataflow.DataFlow
import semmle.javascript.dataflow.TaintTracking

/**
 * @name Express.js request parameters written to sensitive sinks
 * @description Tainted data coming in from HTTP request parameters written to sensitive sinks
 * @kind path-problem
 * @id javascript/module-express-vuln
 * @problem.severity error
 * @tags security
 */
class SBMJsConfig extends TaintTracking::Configuration {
  SBMJsConfig() { this = "SBMJsConfig" }

  override predicate isSource(DataFlow::Node source) {
    exists(ArrowFunctionExpr func, Parameter param |
      func.getParameter(0) = param and
      param.getName() = "req" and
      source.asExpr() = param
    )
  }

  override predicate isSink(DataFlow::Node sink) {
    exists(MethodCallExpr call, DotExpr dotExpr |
      (
        dotExpr.getPropertyName() = "send" or
        dotExpr.getPropertyName() = "write" or
        dotExpr.getPropertyName() = "redirect" or
        dotExpr.getPropertyName() = "createReadStream" or
        dotExpr.getPropertyName() = "writeFileSync" or
        dotExpr.getPropertyName() = "createWriteStream" or
        dotExpr.getPropertyName() = "open" or
        dotExpr.getPropertyName() = "query"
      ) and
      call.getArgument(0) = sink.asExpr()
    )
  }
}

import DataFlow::PathGraph

from SBMJsConfig dataflow, DataFlow::PathNode source, DataFlow::PathNode sink
where dataflow.hasFlow(source.getNode(), sink.getNode())
select source.getNode(), source, sink,
  "Attacker-control property from request parameter parameter are written a sensitive sink."
