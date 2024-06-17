import javascript
import semmle.javascript.dataflow.DataFlow
import semmle.javascript.dataflow.TaintTracking

/**
 * @name Exported method parameters written to `eval`-like functions
 * @description Tainted data coming in from exported method parameters written to `eval`-like functions
 * @kind problem
 * @id javascript/module-export-rce
 * @problem.severity error
 * @tags security
 */
class IchnaeaConfig extends DataFlow::Configuration {

  IchnaeaConfig() { this = "IchnaeaConfig" }

  override predicate isSource(DataFlow::Node source) {
    DataFlow::moduleMember("fs", "readFile").getACall().getArgument(0) = source
  }

  override predicate isSink(DataFlow::Node sink) {
    DataFlow::moduleImport("growl").getACall().getArgument(0) = sink
    or
    DataFlow::moduleMember("child_process", "exec").getACall().getArgument(0) = sink
    or
    DataFlow::moduleMember("child_process", "execSync").getACall().getArgument(0) = sink
    or
    DataFlow::moduleMember("child_process", "execFileSync").getACall().getArgument(0) = sink
    or
    DataFlow::globalVarRef("eval").getACall().getArgument(0) = sink
  }
}

from IchnaeaConfig dataflow, DataFlow::Node source, DataFlow::Node sink
where dataflow.hasFlow(source, sink)
select source, "Data flow from $@ to $@.", source, source.toString(), sink, sink.toString()