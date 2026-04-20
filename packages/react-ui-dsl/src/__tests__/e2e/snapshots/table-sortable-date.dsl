root = Table(
  [
    Col("Name", "name"),
    Col("Salary", "salary", { sortable: true }),
    Col("Joined At", "joinedAt", { format: "date" })
  ],
  data.employees
)