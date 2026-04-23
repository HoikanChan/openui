import {
  resolveStateField,
  type InferStateFieldValue,
  type StateField,
} from "@openuidev/lang-core";
import { useFormName, useOpenUI } from "../context";

export function useStateField<T = unknown>(
  name: string,
  value?: T,
): StateField<InferStateFieldValue<T>> {
  const ctx = useOpenUI();
  const formName = useFormName();

  return resolveStateField<InferStateFieldValue<T>>(
    name,
    value,
    ctx.store ?? null,
    ctx.evaluationContext ?? null,
    (fieldName: string) => ctx.getFieldValue(formName, fieldName),
    (fieldName: string, nextValue: unknown) =>
      ctx.setFieldValue(formName, undefined, fieldName, nextValue),
  );
}
