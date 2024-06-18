import javascript
import semmle.javascript.dataflow.DataFlow
import semmle.javascript.dataflow.TaintTracking

/**
 * @name Exported method parameters written to `eval`-like functions
 * @description Tainted data coming in from exported method parameters written to `eval`-like functions
 * @kind path-problem
 * @id javascript/module-export-rce
 * @problem.severity error
 * @tags security
 */
class IchnaeaConfig extends TaintTracking::Configuration {

  IchnaeaConfig() { this = "IchnaeaConfig" }

  override predicate isSource(DataFlow::Node source) {
    // It is extremely difficult to express a kind of match that finds methods defined to
    // a container that is then exported due to CodeQL undertainting so all method parameters
    // in a file where an export is happening is considered exportable.
    exists(AssignExpr assignment, PropAccess moduleExports, Function exportedFunc, File f |
      moduleExports.getPropertyName() = "exports" and
      moduleExports.getBase().toString() = "module" and
      assignment.getLhs() = moduleExports and
      exportedFunc.getTopLevel() = assignment.getTopLevel() and
      exportedFunc.getFile() = f and
      not isLibraryOrTestFile(f) and
      source.getAstNode() = exportedFunc.getAParameter()
    )
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

  predicate isLibraryOrTestFile(File f) {
    // Exclude common directories or files that contain library code
    f.getRelativePath().regexpMatch("^node_modules/.*") or
    f.getAbsolutePath().regexpMatch(".*/codeql/.*") or
    f.getRelativePath().regexpMatch("^nodejs/.*") or
    f.getRelativePath().regexpMatch("^internal/.*") or
    f.getRelativePath().regexpMatch("^tests/.*")
  }
}

import DataFlow::PathGraph

from IchnaeaConfig dataflow, DataFlow::PathNode source, DataFlow::PathNode sink
where dataflow.hasFlow(source.getNode(), sink.getNode())
select source.getNode(), source, sink, "Properties from possibly exposed parameter are written a sensitive sink."
