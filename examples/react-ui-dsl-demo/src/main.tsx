import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { PiuExtensionDemo } from "./PiuExtensionDemo";

const params = new URLSearchParams(window.location.search);
const Demo = params.get("demo") === "piu-extension" ? PiuExtensionDemo : App;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Demo />
  </StrictMode>,
);
