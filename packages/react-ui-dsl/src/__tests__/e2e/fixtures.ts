export interface Fixture {
  id: string;
  prompt: string;
  dataModel: Record<string, unknown>;
  assert: { contains: string[] };
}

export const fixtures: Record<string, Fixture[]> = {
  Table: [
    {
      id: "table-basic",
      prompt: "Show a regional sales breakdown table",
      dataModel: {
        report: {
          breakdown: [
            { region: "North America", revenue: 1200000, updatedAt: "2026-04-01T00:00:00.000Z" },
            { region: "Europe", revenue: 860000, updatedAt: "2026-04-03T00:00:00.000Z" },
          ],
        },
      },
      assert: { contains: ["North America", "Europe"] },
    },
    {
      id: "table-sortable-date",
      prompt: "Show an employee table with sortable salary and formatted join date",
      dataModel: {
        employees: [
          { name: "Alice", salary: 95000, joinedAt: "2023-06-15T00:00:00.000Z" },
          { name: "Bob", salary: 82000, joinedAt: "2022-01-10T00:00:00.000Z" },
        ],
      },
      assert: { contains: ["Alice", "Bob"] },
    },
  ],
  PieChart: [
    {
      id: "pie-sales-by-region",
      prompt: "Show a pie chart of sales distribution by region",
      dataModel: {
        sales: {
          byRegion: [
            { region: "North America", amount: 1200000 },
            { region: "Europe", amount: 860000 },
            { region: "Asia Pacific", amount: 1050000 },
          ],
        },
      },
      assert: { contains: ["<div"] },
    },
  ],
  LineChart: [
    {
      id: "line-monthly-revenue",
      prompt: "Show monthly revenue trend as a line chart",
      dataModel: {
        metrics: {
          monthly: [
            { month: "Jan", revenue: 420000 },
            { month: "Feb", revenue: 530000 },
            { month: "Mar", revenue: 610000 },
          ],
        },
      },
      assert: { contains: ["<div"] },
    },
  ],
  BarChart: [
    {
      id: "bar-product-comparison",
      prompt: "Compare quarterly revenue across product lines as a bar chart",
      dataModel: {
        products: [
          { name: "Enterprise", q1: 800000, q2: 920000 },
          { name: "SMB", q1: 350000, q2: 410000 },
        ],
      },
      assert: { contains: ["<div"] },
    },
  ],
  GaugeChart: [
    {
      id: "gauge-kpi",
      prompt: "Show a KPI gauge for system health score",
      dataModel: {
        system: { healthScore: 87, label: "System Health" },
      },
      assert: { contains: ["<div"] },
    },
  ],
};
