import type { Meta, StoryObj } from "@storybook/react";
import { Card, ConfigProvider, Space, Typography } from "antd";
import React from "react";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../genui-lib/dslLibrary";

const { Paragraph, Text, Title } = Typography;

const overviewDsl = `root = Table(
  [
    Col("Region", "region"),
    Col("Revenue", "revenue"),
    Col("Updated", "updated", { format: "date", tooltip: true })
  ],
  rows
)

rows = [
  { region: "North America", revenue: "$1.2M", updated: "2026-04-01T00:00:00.000Z" },
  { region: "Europe", revenue: "$860K", updated: "2026-04-03T00:00:00.000Z" },
  { region: "Asia Pacific", revenue: "$1.05M", updated: "2026-04-07T00:00:00.000Z" }
]`;

const meta = {
  title: "DSL/Overview",
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Standalone Storybook smoke test for `@openuidev/react-ui-dsl`. It renders one openui-lang response with the local `dslLibrary` so the package can be validated without the `react-ui` Storybook.",
      },
    },
  },
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

function StoryShell() {
  return (
    <ConfigProvider>
      <div
        style={{
          minHeight: "100vh",
          padding: 32,
          background:
            "linear-gradient(180deg, rgba(245,247,251,1) 0%, rgba(255,255,255,1) 45%, rgba(248,250,252,1) 100%)",
        }}
      >
        <div style={{ margin: "0 auto", maxWidth: 1120 }}>
          <Space direction="vertical" size={24} style={{ width: "100%" }}>
            <div>
              <Title level={2} style={{ marginBottom: 8 }}>
                React UI DSL Overview
              </Title>
              <Paragraph style={{ marginBottom: 0, maxWidth: 760 }}>
                This standalone Storybook validates that the DSL package can boot, parse openui-lang,
                and render through its own component library without relying on the `react-ui`
                Storybook.
              </Paragraph>
            </div>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "minmax(0, 1.1fr) minmax(360px, 0.9fr)",
                gap: 24,
                alignItems: "start",
              }}
            >
              <Card title="Rendered Output">
                <Renderer library={dslLibrary} response={overviewDsl} />
              </Card>

              <Card title="Source Response">
                <Space direction="vertical" size={12} style={{ width: "100%" }}>
                  <Text type="secondary">
                    The renderer consumes openui-lang source directly. This keeps the story close to
                    the real integration path.
                  </Text>
                  <pre
                    style={{
                      margin: 0,
                      padding: 16,
                      overflowX: "auto",
                      whiteSpace: "pre-wrap",
                      borderRadius: 12,
                      background: "#0f172a",
                      color: "#e2e8f0",
                      fontSize: 13,
                      lineHeight: 1.5,
                    }}
                  >
                    {overviewDsl}
                  </pre>
                </Space>
              </Card>
            </div>
          </Space>
        </div>
      </div>
    </ConfigProvider>
  );
}

export const Overview: Story = {
  render: () => <StoryShell />,
};
