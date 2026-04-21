root = VLayout([contactForm])
contactForm = Card([formHeader, formFields])
formHeader = CardHeader("Contact Form", "Please fill out the form below")
formFields = Form([fullNameField, emailField])
fullNameField = {label: "Full Name", name: "fullName", rules: [{required: true}], component: Text("Enter your full name")}
emailField = {label: "Email Address", name: "email", rules: [{required: true}], component: Text("Enter your email")}