// Debug script to test Parser canvas extraction
import { createParser } from "@openuidev/lang-core";
import { dslLibrary } from "@openuidev/react-ui-dsl";

const dsl = `
root = VLayout([text])
text = Text("Hello")
cpuCard = DashboardCard([], "CPU")
`;

console.log("Testing Parser canvas extraction...");
console.log("DSL:", dsl);

const schema = dslLibrary.toJSONSchema();
console.log("Schema $defs:", Object.keys(schema.$defs || {}));

const parser = createParser(schema, "root");
const result = parser.parse(dsl);

console.log("Parse Result:");
console.log("  Root:", result.root?.typeName);
console.log("  Canvas Items:", result.canvasItems?.length ?? 0);
console.log("  Canvas Items details:", result.canvasItems);
console.log("  Statement Count:", result.meta.statementCount);
console.log("  Orphaned:", result.meta.orphaned);
console.log("  Errors:", result.meta.errors);