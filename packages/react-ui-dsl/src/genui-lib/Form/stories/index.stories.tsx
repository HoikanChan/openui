import type { Meta, StoryObj } from "@storybook/react";
import { ButtonsView } from "../../Buttons/view";
import { FormControlView } from "../../FormControl/view";
import { InputView } from "../../Input/view";
import { FormView, type FormViewProps } from "../view";

type FormStoryProps = Omit<FormViewProps, "buttons" | "fields">;

function FormStoryExample(props: FormStoryProps) {
  return (
    <FormView
      {...props}
      buttons={
        <ButtonsView direction="row">
          <button type="submit">Save</button>
          <button type="reset">Cancel</button>
        </ButtonsView>
      }
      fields={
        <>
          <FormControlView
            input={<InputView placeholder="Full name" />}
            label="Name"
            name="name"
            required
          />
          <FormControlView
            input={<InputView placeholder="name@example.com" type="email" />}
            label="Email"
            name="email"
          />
        </>
      }
    />
  );
}

const meta = {
  title: "DSL Components/Form",
  component: FormStoryExample,
  args: {
    initialValues: {
      email: "alice@example.com",
      name: "Alice",
    },
    labelAlign: "left",
    layout: "vertical",
    name: "profile-form",
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
