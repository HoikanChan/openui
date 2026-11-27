"use client";

import { Modal as AntModal } from "antd";
import type { ReactNode } from "react";

export type ModalSize = "sm" | "md" | "lg";

const MODAL_WIDTHS: Record<ModalSize, number> = {
  sm: 400,
  md: 640,
  lg: 900,
};

export type ModalViewProps = {
  title: string;
  open: boolean;
  size?: ModalSize;
  children?: ReactNode;
  onClose: () => void;
};

export function ModalView({ children, onClose, open, size, title }: ModalViewProps) {
  return (
    <AntModal
      centered
      footer={null}
      onCancel={onClose}
      open={open}
      title={title}
      width={size ? MODAL_WIDTHS[size] : undefined}
    >
      {children}
    </AntModal>
  );
}
