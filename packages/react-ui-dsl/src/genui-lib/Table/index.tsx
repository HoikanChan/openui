"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ColSchema, TableSchema } from "./schema";
import { TableView } from "./view";
import { ExtraCol } from "./ExtraCol";
import { IndexCol } from "./IndexCol";

export { ExtraCol, IndexCol };

export const Col = defineComponent({
  name: "Col",
  props: ColSchema,
  description:
    "Declarative table column. Use Col(title, field, options?) instead of writing a raw columns JSON object.",
  component: () => null,
});

export const Table = defineComponent({
  name: "Table",
  props: TableSchema,
  description:
    'Data table: Table(columns, rows, expandRow?). Columns: Col(title, field, opts?) for row fields, IndexCol(title) for row number, ExtraCol(title, value) for shared data or actions. ExpandRow: @Render("row", ...).',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TableSchema>>) => (
    <TableView
      columns={props.columns}
      expandRow={props.expandRow}
      renderNode={renderNode}
      rows={props.rows}
    />
  ),
});
