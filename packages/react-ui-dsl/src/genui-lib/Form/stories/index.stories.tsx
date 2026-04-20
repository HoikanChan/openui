import type { Meta, StoryObj } from "@storybook/react";
import { Input, InputNumber } from "antd";
import { FormView } from "../view";

const meta = {
  title: "DSL Components/Form",
  component: FormView,
  args: {
    fields: [],
    initialValues: {
      budget: 120000,
      project: "react-ui-dsl",
    },
    labelAlign: "left",
    layout: "vertical",
  },
  argTypes: {
    layout: {
      control: "select",
      options: ["vertical", "inline", "horizontal"],
    },
    labelAlign: {
      control: "select",
      options: ["left", "right"],
    },
    initialValues: {
      control: "object",
    },
  },
  render: (args) => (
    <FormView
      {...args}
      fields={[
        { component: <Input placeholder="Project name" />, label: "Project", name: "project" },
        {
          component: <InputNumber min={0} style={{ width: "100%" }} />,
          label: "Budget",
          name: "budget",
          required: true,
        },
      ]}
    />
  ),
} satisfies Meta<typeof FormView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
