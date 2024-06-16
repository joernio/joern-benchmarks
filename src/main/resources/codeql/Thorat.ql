import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.ApiGraphs

/**
 * @name Tainted data in `Flask.request` written to `eval`
 * @description Track tainted data from a Flask HTTP request to a call to `eval`
 * @kind path-problem
 * @id python/http-flask-request-eval
 * @problem.severity error
 * @tags security
 */
module ThoratConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(DataFlow::CallCfgNode call |
      source.asCfgNode() = call.asCfgNode() and
      source = API::moduleImport("flask")
      .getMember("request")
      .getMember("view_args")
      .getMember("get")
      .getACall()
    ) or
    exists(DataFlow::ExprNode expr |
      source.asExpr() = expr.asExpr() and
      source = API::moduleImport("flask")
      .getMember("request")
      .getMember("view_args")
      .asSource()
    )
  }

  predicate isSink(DataFlow::Node sink) {
    exists(DataFlow::CallCfgNode call |
      call = API::builtin("eval").getACall() and
      sink = call.getArg(0)
    )
  }
}

module ThoratFlow = TaintTracking::Global<ThoratConfig>;

import ThoratFlow::PathGraph

from
  ThoratFlow::PathNode source,
  ThoratFlow::PathNode sink
where ThoratFlow::flowPath(source, sink)
select sink.getNode(), source, sink, "Properties from this $@ are written a sensitive sink.",
  source.getNode(), "attacker-controlled HTTP request"
