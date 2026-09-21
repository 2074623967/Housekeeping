import test from "node:test";
import assert from "node:assert/strict";
import { normalizePagePayload } from "./client.js";

test("normalizes a null paged items field to an empty array", () => {
  const payload = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });

  assert.deepEqual(payload.items, []);
  assert.equal(payload.total, 0);
});

test("keeps non-paged payloads and existing arrays unchanged", () => {
  const summary = { totalCount: 0 };
  const items = [{ id: "PAY-001" }];

  assert.equal(normalizePagePayload(summary), summary);
  assert.equal(normalizePagePayload({ items }).items, items);
});
