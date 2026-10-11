import assert from "node:assert/strict";
import test from "node:test";
import {
  buildPaymentAccessTokenStorageKey,
  persistPaymentAccessToken,
  readPaymentAccessToken,
  resolveConfiguredPaymentAccessToken,
  withoutAccessToken
} from "./paymentSession.js";

function createStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => values.set(key, value)
  };
}

test("stores access tokens by source application", () => {
  const storage = createStorage();

  assert.equal(persistPaymentAccessToken("housekeeping-app-web", "app-token", storage), true);
  assert.equal(readPaymentAccessToken("housekeeping-app-web", storage), "app-token");
  assert.equal(readPaymentAccessToken("housekeeping-h5-web", storage), "");
  assert.equal(buildPaymentAccessTokenStorageKey("housekeeping-app-web"),
    "hsp.accessToken.housekeeping-app-web");
});

test("uses configured tokens and only permits demo fallback in development", () => {
  assert.equal(resolveConfiguredPaymentAccessToken(
    { DEV: false, VITE_PAYMENT_ACCESS_TOKEN_APP: " configured-token " },
    "VITE_PAYMENT_ACCESS_TOKEN_APP",
    "demo-token"
  ), "configured-token");
  assert.equal(resolveConfiguredPaymentAccessToken(
    { DEV: true },
    "VITE_PAYMENT_ACCESS_TOKEN_APP",
    "demo-token"
  ), "demo-token");
  assert.equal(resolveConfiguredPaymentAccessToken(
    { DEV: false },
    "VITE_PAYMENT_ACCESS_TOKEN_APP",
    "demo-token"
  ), "");
});

test("fails closed when session storage is unavailable", () => {
  const unavailableStorage = {
    getItem: () => {
      throw new Error("storage unavailable");
    },
    setItem: () => {
      throw new Error("storage unavailable");
    }
  };

  assert.equal(persistPaymentAccessToken("housekeeping-app-web", "app-token", unavailableStorage), false);
  assert.equal(readPaymentAccessToken("housekeeping-app-web", unavailableStorage), "");
});

test("removes legacy access tokens without mutating other route query fields", () => {
  const originalQuery = {
    accessToken: "legacy-token",
    bizType: "balance-pay",
    prepayOrderNo: "PRE-001"
  };

  assert.deepEqual(withoutAccessToken(originalQuery), {
    bizType: "balance-pay",
    prepayOrderNo: "PRE-001"
  });
  assert.equal(originalQuery.accessToken, "legacy-token");
});
