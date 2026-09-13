import assert from "node:assert/strict";
import test from "node:test";

import { PAYMENT_TERMINAL_OPTIONS } from "./paymentTerminal.js";

test("keeps terminal filters aligned with canonical payment terminal values", () => {
  assert.deepEqual(PAYMENT_TERMINAL_OPTIONS, [
    { value: "全部", label: "全部" },
    { value: "H5", label: "H5" },
    { value: "PC", label: "PC" },
    { value: "APP", label: "APP" },
    { value: "MINI_PROGRAM", label: "小程序" },
    { value: "API", label: "API" }
  ]);
});
