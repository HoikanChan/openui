root = Stack([contactForm])
contactForm = Form([fullNameField, emailField], "vertical")
fullNameField = {label: "Full Name", name: "fullName", rules: [{required: true}], component: "text"}
emailField = {label: "Email Address", name: "email", component: "email"}