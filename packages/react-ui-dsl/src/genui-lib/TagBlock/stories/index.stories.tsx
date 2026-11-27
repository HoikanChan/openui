import type { Meta, StoryObj } from "@storybook/react";
import { TagView } from "../../Tag/view";

function TagBlockView({ tags }: { tags: string[] }) {
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
      {tags.map((tag, i) => (
        <TagView key={i} text={tag} />
      ))}
    </div>
  );
}

const meta = {
  title: "DSL Components/TagBlock",
  component: TagBlockView,
  parameters: {
    docs: {
      description: {
        component: "Renders an array of string labels as a wrapped row of tags.",
      },
    },
  },
  args: {
    tags: ["React", "TypeScript", "DSL"],
  },
  argTypes: {
    tags: {
      control: "object",
    },
  },
} satisfies Meta<typeof TagBlockView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const ManyTags: Story = {
  args: {
    tags: ["Landmark", "City Break", "Culture", "Autumn", "Temples", "UNESCO", "Hike"],
  },
};

export const Empty: Story = {
  args: {
    tags: [],
  },
};
