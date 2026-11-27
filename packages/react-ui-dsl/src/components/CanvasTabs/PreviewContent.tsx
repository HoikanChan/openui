"use client";

import React from "react";
import type { Library } from "@cloudsop/openui-react-lang";
import { renderElementNode } from "@cloudsop/openui-react-lang";

export interface PreviewContentProps {
  content: unknown[];
  library: Library;
  dataModel?: Record<string, unknown>;
}

export function PreviewContent({ content, library, dataModel }: PreviewContentProps) {
  return (
    <div style={{ width: "100%", minHeight: "calc(100vh - 120px)", padding: 16 }}>
      {renderElementNode(content, library, dataModel)}
    </div>
  );
}