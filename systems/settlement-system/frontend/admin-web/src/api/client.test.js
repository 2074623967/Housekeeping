import test from "node:test";
import assert from "node:assert/strict";
import { normalizePagePayload } from "./client.js";

test("normalizes empty settlement page items", () => {
  const result = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });

  assert.deepEqual(result.items, []);
  assert.equal(result.total, 0);
});

test("keeps settlement summaries and populated pages unchanged", () => {
  const summary = { pendingCount: 0 };
  const items = [{ settlementNo: "SET-001" }];

  assert.equal(normalizePagePayload(summary), summary);
  assert.equal(normalizePagePayload({ items }).items, items);
});
