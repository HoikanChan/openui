root = VLayout([
  LineChart(
    {
      title: {
        text: "Monthly Revenue Trend"
      },
      xAxis: {
        type: "category",
        data: ["Jan", "Feb", "Mar"]
      },
      yAxis: {
        type: "value"
      }
    },
    data.lineData
  )
])