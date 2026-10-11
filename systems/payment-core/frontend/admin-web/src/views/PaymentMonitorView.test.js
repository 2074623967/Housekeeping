import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "PaymentMonitorView.vue");

test("blocks monitor row selection when the displayed snapshot is stale", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /function pickAlert\(alert\) \{\s*if \(!canDrillDown\.value\)/);
  assert.match(source, /function pickChannel\(item\) \{\s*if \(!canDrillDown\.value\)/);
  assert.equal((source.match(/class="table-link" :disabled="!canDrillDown"/g) || []).length, 2);
});

test("guides success-fact alerts toward source verification without automatic backfill", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /alert\.alertTitle\?\.includes\("支付成功事实不一致"\)[\s\S]*?suggestions\.push\("优先核对预付单绑定、订单来源、支付尝试与渠道流水，结合操作日志查明异常；不得按当前订单行自动补标来源或重发付款。"\)/);
  assert.match(source, /\{\{ selectedAlert\.suggestedAction \}\}/);
});

test("keeps unaccepted channel submissions visible without suggesting premature queries", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /alert\.alertType === "CHANNEL_SUBMIT_UNCONFIRMED"[\s\S]*?勿主动查单或重复提交/);
  assert.match(source, /channel\.unacceptedCount[\s\S]*?核对渠道受理结果，勿主动查单或重复提交/);
  assert.match(source, /channel\.pendingCount \|\| 0\) === 0 && \(channel\.unacceptedCount \|\| 0\) === 0/);
  assert.match(source, /<th>受理未确认<\/th>[\s\S]*?\{\{ item\.unacceptedCount \}\}/);
});
