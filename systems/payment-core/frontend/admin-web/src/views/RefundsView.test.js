import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";

const source = readFileSync(fileURLToPath(new URL("./RefundsView.vue", import.meta.url)), "utf8");

test("keeps diagnostic refund lists readable and their actions disabled", () => {
  assert.match(source, /const canUseSnapshot = computed\(\(\) => !isWriteDisabled\.value\)/);
  assert.match(source, /const isActionDisabled = computed\(\(\) => isWriteDisabled\.value \|\| isDiagnosticView\.value\)/);
  assert.match(source, /if \(isActionDisabled\.value \|\| !loadedQuery\.value\)/);
  assert.match(source, /if \(isActionDisabled\.value \|\| !loadedQuery\.value \|\| !refundItem/);
  assert.match(source, /:disabled="isActionDisabled \|\| !canRunRefundAction\(item\.status, 'retry'\)"/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| isDiagnosticView" @click="openRefundDetail/);
  assert.match(source, /诊断筛选仅供核查和导出/);
  assert.match(source, /isDiagnosticView \? "本页退款金额（异常值不汇总）"/);
  assert.match(source, /isDiagnosticView \? "—" : metrics\.refundAmountTotal/);
  assert.match(source, /item\.refundAmount \?\? "—"/);
});

test("refreshes the refund list after failed applications and actions", () => {
  const applyHandler = source.match(/async function handleApply\(\) \{([\s\S]*?)\n\}\n\nasync function runRefundAction/)?.[1];
  const actionHandler = source.match(/async function runRefundAction\([^)]*\) \{([\s\S]*?)\n\}\n\nfunction goToPage/)?.[1];
  assert.ok(applyHandler);
  assert.ok(actionHandler);
  assert.match(applyHandler, /catch \(error\) \{[\s\S]*?await loadRefunds\(selectedItem\.value\?\.refundOrderId, true, query\);/);
  assert.match(actionHandler, /catch \(error\) \{[\s\S]*?await loadRefunds\(refundOrderId, true, query\);/);
  assert.match(source, /hasLoadedData\.value && \(loadErrorMessage\.value \|\| actionRefreshErrorMessage\.value\)/);
  assert.match(source, /退款动作后列表刷新失败，当前状态未经确认/);
});
