"use client";

import { defineComponent } from "@openuidev/react-lang";
import styles from "./card.module.css";
import { CardSchema } from "./schema";

function cx(...classes: (string | undefined | false | null)[]): string {
  return classes.filter(Boolean).join(" ");
}

const variantClass: Record<string, string> = {
  card: styles.variantCard,
  clear: styles.variantClear,
  sunk: styles.variantSunk,
};

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description:
    "Card container with optional header (title, subtitle, actions) and visual variant (card/clear/sunk)",
  component: ({ props, renderNode }) => {
    const { variant = "card", width = "standard", header } = props;
    const hasHeader =
      header && (header.title || header.subtitle || header.actions?.length);

    return (
      <div
        className={cx(
          styles.root,
          variantClass[variant],
          width === "full" ? styles.widthFull : styles.widthStandard,
        )}
        style={props.style as React.CSSProperties | undefined}
      >
        {hasHeader && (
          <div className={styles.header}>
            <div className={styles.headerLeft}>
              {header.title && (
                <div className={styles.headerTitle}>{header.title}</div>
              )}
              {header.subtitle && (
                <div className={styles.headerSubtitle}>{header.subtitle}</div>
              )}
            </div>
            {header.actions?.length ? (
              <div className={styles.headerActions}>
                {renderNode(header.actions)}
              </div>
            ) : null}
          </div>
        )}
        <div className={styles.body}>{renderNode(props.children)}</div>
      </div>
    );
  },
});
