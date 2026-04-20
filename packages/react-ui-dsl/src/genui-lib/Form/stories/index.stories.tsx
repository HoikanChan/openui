import type { Meta, StoryObj } from "@storybook/react";
import { Input, InputNumber } from "antd";
import { FormView, type FormViewProps } from "../view";

type FormStoryProps = Omit<FormViewProps, "fields">;

function FormStoryExample(props: FormStoryProps) {
  return (
    <FormView
      {...props}
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
  );
}

const meta = {
  title: "DSL Components/Form",
  component: FormStoryExample,
  args: {
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
} satisfies Meta<typeof FormStoryExample>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
