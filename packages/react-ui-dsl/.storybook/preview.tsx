import type { Preview } from "@storybook/react";

declare global {
  // Injected by Storybook's Vite config before the preview module loads.
  var __REACT_UI_DSL_ENV__: { REACT_UI_DSL_VIEW_TARGET?: string } | undefined;
}

if (globalThis.__REACT_UI_DSL_ENV__?.REACT_UI_DSL_VIEW_TARGET !== "eview") {
  void import("antd/dist/reset.css");
}

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    layout: "fullscreen",
    backgrounds: {
      default: "canvas",
      values: [
        {
          name: "canvas",
          value: "#f5f7fb",
        },
      ],
    },
  },
};

export default preview;
