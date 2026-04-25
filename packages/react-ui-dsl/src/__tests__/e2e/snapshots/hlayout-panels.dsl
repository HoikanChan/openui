```openui-lang
root = VLayout([panels])
panels = HLayout([leftPanel, rightPanel], "m", true, "stretch")
leftPanel = Card([leftContent], "card", "column", "m")
leftContent = Text("Left Panel Content")
rightPanel = Card([rightContent], "card", "column", "m")
rightContent = Text("Right Panel Content")
```