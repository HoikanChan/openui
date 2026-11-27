"use client";

import {
  type ComponentRenderProps,
  defineComponent,
  type SubComponentOf,
} from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ColSchema, TableSchema } from "./schema";
import { type ColViewProps, TableView } from "./view";

export const Col = defineComponent({
  name: "Col",
  props: ColSchema,
  description:
    "Declarative table column. Use Col(title, field, options?) instead of writing a raw columns JSON object.",
  component: () => null,
});

type ColumnValue = ColViewProps | SubComponentOf<ColViewProps>;

function isColumnNode(value: ColumnValue): value is SubComponentOf<ColViewProps> {
  return typeof value === "object" && value !== null && "type" in value && "props" in value;
}

function getColumnProps(column: ColumnValue): ColViewProps {
  return isColumnNode(column) ? column.props : column;
}

export const Table = defineComponent({
  name: "Table",
  props: TableSchema,
  description:
    'Data table: Table(columns, rows, expandRow?). Col(title, field, options?) defines each column. For nested array fields, pass @Render("item", Table([childCols], item.nestedField)) as 3rd arg — rows auto-expand when ≤3 parents.',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TableSchema>>) => (
    <TableView
      columns={props.columns.map((column: z.infer<typeof ColSchema> | ColumnValue) =>
        getColumnProps(column as ColumnValue),
      )}
      expandRow={props.expandRow}
      renderNode={renderNode}
      rows={props.rows}
    />
  ),
});
