"use client";

import type { TextCalloutProps } from "../schema";

export type CalloutVariant = NonNullable<TextCalloutProps["variant"]>;

export type CalloutAppearance = {
  accentColor: string;
  backgroundColor: string;
};

const CALLOUT_APPEARANCE: Record<CalloutVariant, CalloutAppearance> = {
  neutral: { accentColor: "#8c8c8c", backgroundColor: "#f5f5f5" },
  info: { accentColor: "#1677ff", backgroundColor: "#e6f4ff" },
  warning: { accentColor: "#faad14", backgroundColor: "#fffbe6" },
  success: { accentColor: "#52c41a", backgroundColor: "#f6ffed" },
  danger: { accentColor: "#ff4d4f", backgroundColor: "#fff2f0" },
};

export function resolveCalloutAppearance(variant?: CalloutVariant): CalloutAppearance {
  return CALLOUT_APPEARANCE[variant ?? "neutral"];
}

export type TextCalloutViewProps = TextCalloutProps;

export function TextCalloutView({ description, title, variant }: TextCalloutViewProps) {
  const appearance = resolveCalloutAppearance(variant);

  return (
    <div
      role="note"
      style={{
        backgroundColor: appearance.backgroundColor,
        borderInlineStart: `4px solid ${appearance.accentColor}`,
        borderRadius: 4,
        padding: "8px 12px",
      }}
    >
      {title ? <div style={{ fontWeight: 600 }}>{title}</div> : null}
      {description ? <div>{description}</div> : null}
    </div>
  );
}
