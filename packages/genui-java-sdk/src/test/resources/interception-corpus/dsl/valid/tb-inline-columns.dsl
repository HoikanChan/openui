root = Table([Col("City", "city"), Col("Population", "population", {sortable: true, cell: @Render("v", TextContent(@FormatNumber(v, 0, "en-US")))}), Col("Country", "country")], data.cities)
