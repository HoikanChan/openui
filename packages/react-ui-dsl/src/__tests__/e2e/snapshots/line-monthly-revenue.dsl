root = Stack([monthlyRevenueChart])
monthlyRevenueChart = LineChart(data.labels, [revenueSeries], "smooth", "Month", "Revenue ($)")
revenueSeries = Series(data.series[0].category, data.series[0].values)