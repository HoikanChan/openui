root = Stack([contactForm])
contactForm = Form([
  {
    label: "Full Name",
    name: "fullName",
    component: Input("Enter your full name", "", false, false, "medium", false, "text")
  },
  {
    label: "Email Address",
    name: "email",
    component: Input("Enter your email address", "", false, false, "medium", false, "email")
  }
], "vertical", "left")
