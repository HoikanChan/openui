import { vi } from "vitest";

Object.defineProperty(globalThis, "ResizeObserver", {
  writable: true,
  configurable: true,
  value: vi.fn().mockImplementation(function () {
    this.observe = vi.fn();
    this.unobserve = vi.fn();
    this.disconnect = vi.fn();
  }),
});
