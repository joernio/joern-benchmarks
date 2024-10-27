import io.shiftleft.codepropertygraph.schema.*
import flatgraph.schema.SchemaBuilder
import flatgraph.schema.Property.ValueType

object CpgExtSchema {
  val builder   = new SchemaBuilder(domainShortName = "Cpg", basePackage = "io.shiftleft.codepropertygraph.generated")
  val cpgSchema = new CpgSchema(builder)
  val instance  = builder.build
}
