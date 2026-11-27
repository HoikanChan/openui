const COMPONENT_HINTS: Record<string, string> = {
  table: "Table",
  piechart: "PieChart",
  linechart: "LineChart",
  barchart: "BarChart",
  hbar: "HorizontalBarChart",
  horizontalbarchart: "HorizontalBarChart",
  area: "AreaChart",
  areachart: "AreaChart",
  radar: "RadarChart",
  radarchart: "RadarChart",
  scatter: "ScatterChart",
  scatterchart: "ScatterChart",
  series: "Series",
  vlayout: "VLayout",
  hlayout: "HLayout",
  text: "Text",
  button: "Button",
  select: "Select",
  image: "Image",
  link: "Link",
  card: "Card",
  descriptions: "Descriptions",
  form: "Form",
  tabs: "Tabs",
};

export function inferFuzzPrompt(id: string): string {
  const hint = (id.split("-")[0] ?? "").toLowerCase();
  const component = COMPONENT_HINTS[hint];
  return component
    ? `Show a ${component} for the given data`
    : "Show an appropriate component for the given data";
}
