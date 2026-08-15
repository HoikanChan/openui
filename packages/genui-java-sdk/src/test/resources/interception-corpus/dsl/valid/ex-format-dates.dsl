root = Stack([dateText, dateTimeText, timeText])
dateText = TextContent("Date: " + @FormatDate(data.ts, "date"))
dateTimeText = TextContent("DateTime: " + @FormatDate(data.ts, "dateTime"))
timeText = TextContent("Time: " + @FormatDate(data.ts, "time"))
