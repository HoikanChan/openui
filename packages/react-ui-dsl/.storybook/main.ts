import type { StorybookConfig } from "@storybook/react-vite";
import path from "path";
import { mergeConfig } from "vite";

const config: StorybookConfig = {
  stories: ["../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"],
  addons: ["@storybook/addon-essentials", "@storybook/addon-interactions", "@storybook/blocks"],
  framework: "@storybook/react-vite",
  previewHead: (head) => `
    ${head}
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/normalize.css@8.0.1/normalize.css">
  `,
  viteFinal: async (config) =>
    mergeConfig(config, {
      server: {
        allowedHosts: [".trycloudflare.com", "127.0.0.1", "localhost"],
      },
      resolve: {
        alias: {
          "@openuidev/react-lang": path.resolve(__dirname, "../../react-lang/src/index.ts"),
          "@openuidev/lang-core": path.resolve(__dirname, "../../lang-core/src/index.ts"),
        },
      },
      optimizeDeps: {
        exclude: ["@openuidev/react-lang", "@openuidev/lang-core"],
        include: ["react", "react-dom", "antd", "echarts", "react-markdown"],
      },
      build: {
        commonjsOptions: {
          include: [/@openuidev\/react-lang/, /@openuidev\/lang-core/, /node_modules/],
        },
      },
    }),
};

export default config;
