root = VLayout(
  [
    LineChart(
      data.lineData,
      { text: "Monthly Revenue Trend" },
      undefined,
      undefined,
      {
        type: "category",
        data: ["Jan", "Feb", "Mar"]
      },
      {
        type: "value"
      }
    )
  ]
)
