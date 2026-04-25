root = VLayout([contactForm])
contactForm = Form([fullNameField, emailField], "vertical", "left")
fullNameField = {label: "Full Name", name: "fullName", rules: [{required: true}], component: Text("")}
emailField = {label: "Email Address", name: "email", rules: [{required: true}], component: Text("")}