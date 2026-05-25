root = Stack([contactForm], "column", "m")
contactForm = Card([formHeader, fullNameField, emailField, submitButton], "card", "standard")
formHeader = CardHeader("Contact Information", "Please fill in your details")
fullNameField = FormField("Full Name", "fullName", "text", true)
emailField = FormField("Email Address", "email", "email", true)
submitButton = Button("Submit", "primary", false, "default")
FormField = (label, name, type, required) => {
  field = {label: label, name: name, rules: [{required: required}], component: TextContent("", "default")}
  if (type == "email") {
    field.component = TextContent("", "default")
  } else {
    field.component = TextContent("", "default")
  }
  return field
}