"use client";

import type { CSSProperties } from "react";
import type { SelectProps } from "../schema";

export type SelectOption = NonNullable<SelectProps["items"]>[number];

export type SelectViewProps = Pick<SelectProps, "items" | "placeholder" | "value"> & {
  style?: CSSProperties;
};
