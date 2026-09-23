import assert from "node:assert/strict";
import test from "node:test";
import {
  buildAppWebCashierUrl,
  buildAppWebRefundProgressUrl,
  buildReconciliationChainUrl
} from "./externalUrls.js";

test("builds configured cashier URLs with encoded identifiers", () => {
  assert.equal(
    buildAppWebCashierUrl("PRE/1?#%", "https://pay.example.com/app/?stale=1#old"),
    "https://pay.example.com/app/cashier/PRE%2F1%3F%23%25"
  );
  assert.equal(
    buildAppWebCashierUrl("PRE-1", "  https://pay.example.com/app///  "),
    "https://pay.example.com/app/cashier/PRE-1"
  );
  assert.equal(buildAppWebCashierUrl("PRE-1"), "http://127.0.0.1:5175/cashier/PRE-1");
});

test("rejects unsafe cashier URL inputs", () => {
  assert.throws(() => buildAppWebCashierUrl(" "), /required/);
  assert.throws(() => buildAppWebCashierUrl("PRE-1", "javascript:alert(1)"), /HTTP or HTTPS/);
});

test("builds configured refund progress URLs with encoded identifiers", () => {
  assert.equal(
    buildAppWebRefundProgressUrl("REF/1?#%", "https://pay.example.com/app/?stale=1#old"),
    "https://pay.example.com/app/refund-progress/REF%2F1%3F%23%25"
  );
  assert.equal(buildAppWebRefundProgressUrl("REF-1"), "http://127.0.0.1:5175/refund-progress/REF-1");
});

test("rejects empty refund progress URL inputs", () => {
  assert.throws(() => buildAppWebRefundProgressUrl(" "), /required/);
});

test("builds a reconciliation chain diagnostic url", () => {
  assert.equal(
    buildReconciliationChainUrl("CHAIN/001?#%", "https://recon.example.com/ops/?stale=1#old"),
    "https://recon.example.com/ops/?businessChainId=CHAIN%2F001%3F%23%25"
  );
  assert.equal(
    buildReconciliationChainUrl("CHAIN-001"),
    "http://127.0.0.1:5180/?businessChainId=CHAIN-001"
  );
});

test("rejects an empty reconciliation chain id", () => {
  assert.throws(() => buildReconciliationChainUrl(" "), /required/);
});
