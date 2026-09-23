import assert from "node:assert/strict";
import test from "node:test";
import { systemCatalog, systemGroups } from "./systemCatalog.js";

test("keeps every local platform entry reachable from the catalog", () => {
  assert.equal(systemCatalog.length, 15);
  assert.equal(systemCatalog.filter((item) => item.kind === "admin").length, 12);
  assert.equal(systemCatalog.filter((item) => item.kind === "terminal").length, 3);
  assert.ok(systemCatalog.every((item) => /^https?:\/\//.test(item.baseUrl)));
  assert.equal(systemCatalog.filter((item) => item.current).map((item) => item.code).join(","), "payment-core");
});

test("preserves domain grouping order for the system directory", () => {
  assert.deepEqual(systemGroups(), ["支付核心", "资金域", "平台治理"]);
});
