root = Table(
  [Col("Region", "region"), Col("Revenue", "revenue"), Col("Updated At", "updatedAt", { format: "date" })],
  data.report.breakdown
)