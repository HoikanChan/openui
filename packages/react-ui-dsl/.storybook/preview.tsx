import type { Preview } from "@storybook/react";
import "antd/dist/reset.css";
import React from "react";

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
