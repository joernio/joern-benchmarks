import io.shiftleft.codepropertygraph.schema.*
import overflowdb.schema.EdgeType.Cardinality
import overflowdb.schema.{Constant, SchemaBuilder}
import overflowdb.schema.Property.ValueType

object CpgExtSchema {
  val builder   = new SchemaBuilder(domainShortName = "Cpg", basePackage = "io.shiftleft.codepropertygraph.generated")
  val cpgSchema = new CpgSchema(builder)
  val instance  = builder.build
}
