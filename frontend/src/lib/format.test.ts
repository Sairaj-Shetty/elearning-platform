import { describe, expect, it } from "vitest";
import { formatDuration, progressLabel } from "./format";

describe("formatters", () => {
  it("formats course length without inventing a percent", () => {
    expect(formatDuration(75)).toBe("1h 15m");
    expect(formatDuration(0)).toBe("Time not set");
    expect(progressLabel(null)).toBe("Not started");
    expect(progressLabel(33.3)).toBe("33.3% from saved lessons");
  });
});
