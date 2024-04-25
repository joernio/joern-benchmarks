import io.shiftleft.codepropertygraph.schema.*
import overflowdb.schema.EdgeType.Cardinality
import overflowdb.schema.{Constant, SchemaBuilder}
import overflowdb.schema.Property.ValueType

class CpgExtSchema(builder: SchemaBuilder, cpgSchema: CpgSchema) {

  private val cfgNode     = cpgSchema.cfg.cfgNode
  private val findingNode = cpgSchema.finding.finding

  val sourceNode = builder
    .addNodeType("SOURCE_NODE", comment = "A sensitive data source.")

  val sinkNode = builder
    .addNodeType("SINK_NODE", comment = "A sensitive data sink.")

  val matches = builder
    .addEdgeType("MATCHES")

  cfgNode.addInEdge(matches, sourceNode, stepNameIn = "matchingSources", stepNameOut = "matchedNodes")
  cfgNode.addInEdge(matches, sinkNode, stepNameIn = "matchingSinks", stepNameOut = "matchedNodes")

}

object CpgExtSchema {
  val builder   = new SchemaBuilder(domainShortName = "Cpg", basePackage = "io.shiftleft.codepropertygraph.generated")
  val cpgSchema = new CpgSchema(builder)
  val cpgExtSchema = new CpgExtSchema(builder, cpgSchema)
  val instance     = builder.build
}
