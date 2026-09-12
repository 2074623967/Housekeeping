import assert from "node:assert/strict";
import test from "node:test";
import { previousShanghaiBusinessDate } from "./businessDate.js";

test("resolves the previous business date in Asia/Shanghai", () => {
  assert.equal(
    previousShanghaiBusinessDate(new Date("2026-09-11T16:30:00.000Z")),
    "2026-09-11"
  );
});

test("resolves the previous Shanghai business date across year boundaries", () => {
  assert.equal(
    previousShanghaiBusinessDate(new Date("2025-12-31T16:30:00.000Z")),
    "2025-12-31"
  );
});

test("rejects invalid business date clocks", () => {
  assert.throws(
    () => previousShanghaiBusinessDate(new Date("invalid")),
    /valid Date/
  );
});
