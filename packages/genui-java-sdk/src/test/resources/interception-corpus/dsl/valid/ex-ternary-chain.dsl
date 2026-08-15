root = Stack([simple, grade])
simple = TextContent(data.ok ? "yes" : "no")
grade = TextContent(data.score >= 90 ? "A" : data.score >= 80 ? "B" : data.score >= 70 ? "C" : "F")
