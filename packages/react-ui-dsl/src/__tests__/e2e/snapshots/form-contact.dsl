root = VLayout([contactForm])
contactForm = Form([fullNameField, emailField], "vertical")
fullNameField = {label: "Full Name", name: "fullName", rules: [{required: true}], component: null}
emailField = {label: "Email Address", name: "email", rules: [{required: true}], component: null}