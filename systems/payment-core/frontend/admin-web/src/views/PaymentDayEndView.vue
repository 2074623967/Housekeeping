<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentDayEndApi } from "../api/client";
import { isClosedShanghaiBusinessDate, previousShanghaiBusinessDate } from "../utils/businessDate";

const router = useRouter();
const overview = ref({
  totalBatchCount: 0,
  completedBatchCount: 0,
  abnormalBatchCount: 0,
  latestBizDate: "",
  latestBatchStatus: "未跑批",
  openChannelAbnormalCount: 0,
  openInternalAbnormalCount: 0,
  openPendingPaymentCount: 0,
  openMissingPaymentSuccessTimeCount: 0,
  openPaymentSuccessFactMismatchCount: 0,
  openPendingRefundCount: 0,
  openRefundFailCount: 0,
  latestRefundFailAmount: "¥0.00",
  openMissingRefundSuccessTimeCount: 0,
  openRefundTerminalFactMismatchCount: 0,
  alerts: [],
  recentBatches: []
});
const isLoading = ref(false);
const hasLoadedData = ref(false);
const errorMessage = ref("");
const actionResultMessage = ref("");
const actionErrorMessage = ref("");
const activeAction = ref("");
const actionRunning = computed(() => isLoading.value || Boolean(activeAction.value));
const isSnapshotStale = computed(() => hasLoadedData.value && Boolean(errorMessage.value));
const canExport = computed(() => hasLoadedData.value
  && overview.value.totalBatchCount > 0
  && !isSnapshotStale.value);
const canDrillDown = computed(() => hasLoadedData.value
  && !actionRunning.value
  && !isSnapshotStale.value);

const latestClosedBusinessDate = previousShanghaiBusinessDate();
const runForm = ref({
  bizDate: latestClosedBusinessDate,
  triggeredBy: "",
  runMode: "MANUAL"
});

async function loadOverview() {
  if (actionRunning.value) {
    return;
  }
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentDayEndApi.getOverview();
    hasLoadedData.value = true;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

async function runBatch() {
  if (actionRunning.value) {
    return;
  }
  if (!hasLoadedData.value || isSnapshotStale.value) {
    actionErrorMessage.value = "当前日终快照不可操作，请刷新成功后再触发跑批。";
    actionResultMessage.value = "";
    return;
  }
  if (!isClosedShanghaiBusinessDate(runForm.value.bizDate)) {
    actionErrorMessage.value = "业务日期必须早于上海当前日期。";
    actionResultMessage.value = "";
    return;
  }
  const triggeredBy = runForm.value.triggeredBy.trim();
  if (!triggeredBy) {
    actionErrorMessage.value = "请先输入触发人。";
    actionResultMessage.value = "";
    return;
  }
  activeAction.value = "run";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    overview.value = await paymentDayEndApi.runBatch({ ...runForm.value, triggeredBy });
    hasLoadedData.value = true;
    actionResultMessage.value = `已触发 ${runForm.value.bizDate} 的支付日终处理。`;
  } catch (error) {
    actionErrorMessage.value = `支付日终处理触发失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

function exportBatches() {
  if (actionRunning.value || !canExport.value) {
    return;
  }
  const exportUrl = paymentDayEndApi.buildExportUrl();
  window.open(exportUrl, "_blank", "noopener");
}

function openDrillDown(route) {
  if (!canDrillDown.value || !route) {
    return;
  }
  router.push(route);
}

onMounted(loadOverview);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付日终处理</h2>
        <p>统一收口支付、退款、渠道回调和内部事件，作为后续对账与差错处理的前置事实</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" :disabled="actionRunning || !canExport" @click="exportBatches">导出 CSV</button>
        <button class="button secondary" :disabled="actionRunning" @click="loadOverview">
          {{ isLoading ? "刷新中..." : "刷新" }}
        </button>
      </div>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        {{ hasLoadedData ? "支付日终数据刷新失败，当前展示上次成功快照：" : "支付日终数据加载失败：" }}{{ errorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="success-banner">
        {{ actionResultMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付日终数据加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付日终快照</div>

      <template v-else>
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">累计跑批次数</div>
            <div class="detail-value">{{ overview.totalBatchCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">完成批次数</div>
            <div class="detail-value">{{ overview.completedBatchCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">异常批次数</div>
            <div class="detail-value">{{ overview.abnormalBatchCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">最近业务日</div>
            <div class="detail-value">{{ overview.latestBizDate || "暂无" }}</div>
          </div>
        </div>

        <div class="risk-banner">
          当前未收口：支付 {{ overview.openPendingPaymentCount ?? "未采集" }} 笔，支付成功时间缺失 {{ overview.openMissingPaymentSuccessTimeCount ?? "未采集" }} 笔，支付成功事实不一致 {{ overview.openPaymentSuccessFactMismatchCount ?? "未采集" }} 笔，退款失败 {{ overview.openRefundFailCount ?? "未采集" }} 笔，退款成功时间缺失 {{ overview.openMissingRefundSuccessTimeCount ?? "未采集" }} 笔，退款终态事实不一致 {{ overview.openRefundTerminalFactMismatchCount ?? "未采集" }} 笔，支付成功差异 {{ overview.latestPaymentSuccessGapCount ?? 0 }} 笔，渠道异常 {{ overview.openChannelAbnormalCount }} 笔，内部事件异常 {{ overview.openInternalAbnormalCount }} 笔，待收口退款 {{ overview.openPendingRefundCount }} 笔。
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>对账准入判断</h3>
              <p class="meta">日终处理 V1.8 已给出统一准入口径，先判断是否能进入正式对账，再安排差错处理顺序</p>
            </div>
          </div>
          <div class="detail-card-grid">
            <div class="detail-card">
              <div class="detail-label">当前结论</div>
              <div class="detail-value">
                <span :class="['badge', overview.reconciliationReadinessType]">
                  {{ overview.reconciliationReadinessStatus || "未生成日终事实" }}
                </span>
              </div>
              <div class="detail-hint">{{ overview.reconciliationReadinessSummary }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">建议动作</div>
              <div class="detail-hint">{{ overview.reconciliationSuggestedAction || "先执行日终跑批并补齐事实快照" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">责任方</div>
              <div class="detail-value">{{ overview.reconciliationBlockingOwner || "支付研发 / 财务值班" }}</div>
              <div class="detail-hint">用于安排次日对账前的责任归口</div>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>最近批次对账前置事实</h3>
              <p class="meta">用于财务和运营先确认支付成功、渠道收口、内部事件收口是否一致，再决定是否进入正式对账。</p>
            </div>
          </div>
          <div class="detail-card-grid">
            <div class="detail-card">
              <div class="detail-label">渠道成功收口</div>
              <div class="detail-value">{{ overview.latestChannelSuccessCount ?? 0 }}</div>
              <div class="detail-hint">金额 {{ overview.latestChannelSuccessAmount || "¥0.00" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">内部事件成功</div>
              <div class="detail-value">{{ overview.latestInternalSuccessCount ?? 0 }}</div>
              <div class="detail-hint">金额 {{ overview.latestInternalSuccessAmount || "¥0.00" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">支付成功差异</div>
              <div class="detail-value">{{ overview.latestPaymentSuccessGapCount ?? 0 }}</div>
              <div class="detail-hint">金额 {{ overview.latestPaymentSuccessGapAmount || "¥0.00" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">待收口支付</div>
              <div class="detail-value">{{ overview.openPendingPaymentCount ?? "未采集" }}</div>
              <div class="detail-hint">金额 {{ overview.latestPendingPaymentAmount || "未采集" }}</div>
              <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown('/payments?status=DAY_END_PENDING')">查看支付单</button>
            </div>
            <div class="detail-card">
              <div class="detail-label">支付成功时间缺失</div>
              <div class="detail-value">{{ overview.openMissingPaymentSuccessTimeCount ?? "未采集" }}</div>
              <div class="detail-hint">缺少可信账期归属，必须先核查</div>
              <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown('/payments?status=SUCCESS_TIME_MISSING')">查看支付单</button>
            </div>
            <div class="detail-card">
              <div class="detail-label">支付成功事实不一致</div>
              <div class="detail-value">{{ overview.openPaymentSuccessFactMismatchCount ?? "未采集" }}</div>
              <div class="detail-hint">支付单与最新支付尝试未形成一致成功终态</div>
              <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown('/payments?status=SUCCESS_FACT_MISMATCH')">查看支付单</button>
            </div>
            <div class="detail-card">
              <div class="detail-label">退款失败</div>
              <div class="detail-value">{{ overview.openRefundFailCount ?? "未采集" }}</div>
              <div class="detail-hint">金额 {{ overview.latestRefundFailAmount || "未采集" }}</div>
              <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown('/refunds?refundStatus=FAIL')">查看退款单</button>
            </div>
            <div class="detail-card">
              <div class="detail-label">退款成功时间缺失</div>
              <div class="detail-value">{{ overview.openMissingRefundSuccessTimeCount ?? "未采集" }}</div>
              <div class="detail-hint">逆向资金账期无法可信归属，必须先核查</div>
              <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown('/refunds?refundStatus=SUCCESS_TIME_MISSING')">查看退款单</button>
            </div>
            <div class="detail-card">
              <div class="detail-label">退款终态事实不一致</div>
              <div class="detail-value">{{ overview.openRefundTerminalFactMismatchCount ?? "未采集" }}</div>
              <div class="detail-hint">退款单与最新渠道尝试未形成一致终态</div>
              <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown('/refunds?refundStatus=TERMINAL_FACT_MISMATCH')">查看退款单</button>
            </div>
            <div class="detail-card">
              <div class="detail-label">待收口退款金额</div>
              <div class="detail-value">{{ overview.latestPendingRefundAmount || "¥0.00" }}</div>
              <div class="detail-hint">对应 {{ overview.openPendingRefundCount ?? 0 }} 笔退款</div>
            </div>
          </div>
        </div>

        <div v-if="!overview.alerts.length" class="state-box">当前日终快照暂无需处理的风险告警</div>
        <div v-else class="detail-card-grid">
          <div v-for="alert in overview.alerts" :key="alert.alertType" class="detail-card">
            <div class="detail-label">{{ alert.alertTitle }}</div>
            <div class="detail-value">{{ alert.affectedCount }}</div>
            <div class="detail-hint">{{ alert.alertMessage }}</div>
            <div class="detail-hint">{{ alert.suggestedAction }}</div>
            <button class="link-button" :disabled="!canDrillDown" @click="openDrillDown(alert.actionRoute)">查看</button>
          </div>
        </div>

        <div class="sub-panel">
          <h3>手动触发日终处理</h3>
          <p>适用于补跑、账期重检和渠道异常排查，当前 V1 会同步生成一条日终批次留痕。</p>

          <div class="toolbar compact-toolbar">
            <div class="field">
              <label>业务日期</label>
              <input v-model.trim="runForm.bizDate" type="date" :max="latestClosedBusinessDate" :disabled="actionRunning" />
            </div>
            <div class="field">
              <label>运行方式</label>
              <select v-model="runForm.runMode" :disabled="actionRunning">
                <option value="MANUAL">MANUAL</option>
                <option value="RERUN">RERUN</option>
              </select>
            </div>
            <div class="field wide-field">
              <label>触发人</label>
              <input v-model.trim="runForm.triggeredBy" maxlength="64" placeholder="请输入触发人" :disabled="actionRunning" />
            </div>
            <div class="toolbar-actions">
              <button
                class="button primary"
                :disabled="actionRunning || Boolean(errorMessage) || !hasLoadedData"
                @click="runBatch"
              >
                {{ activeAction === "run" ? "跑批处理中..." : "触发跑批" }}
              </button>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>最近日终批次</h3>
              <p class="meta">重点关注待收口支付、退款失败、成功时间缺失、支付与退款终态事实不一致、支付成功差异、渠道异常、内部事件异常和待收口退款，作为第二天对账和差错处理入口</p>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>批次号</th>
                  <th>业务日期</th>
                  <th>运行方式</th>
                  <th>批次状态</th>
                  <th>支付总单量</th>
                  <th>支付成功单量</th>
                  <th>支付成功金额</th>
                  <th>渠道成功单量</th>
                  <th>渠道成功金额</th>
                  <th>内部成功单量</th>
                  <th>内部成功金额</th>
                  <th>成功差异单量</th>
                  <th>成功差异金额</th>
                  <th>待收口支付</th>
                  <th>待收口支付金额</th>
                  <th>支付成功时间缺失</th>
                  <th>支付成功事实不一致</th>
                  <th>退款成功单量</th>
                  <th>退款成功金额</th>
                  <th>退款失败单量</th>
                  <th>退款失败金额</th>
                  <th>退款成功时间缺失</th>
                  <th>退款终态事实不一致</th>
                  <th>渠道异常</th>
                  <th>内部异常</th>
                  <th>待收口退款</th>
                  <th>待收口退款金额</th>
                  <th>对账准入</th>
                  <th>备注</th>
                  <th>触发人</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!overview.recentBatches.length">
                  <td colspan="31" class="state-box">当前暂无支付日终批次</td>
                </tr>
                <tr v-for="item in overview.recentBatches" :key="item.batchNo">
                  <td><button class="table-link" :disabled="!canDrillDown" @click="openDrillDown(`/payment-day-end/batches/${encodeURIComponent(item.batchNo)}`)">{{ item.batchNo }}</button></td>
                  <td>{{ item.bizDate }}</td>
                  <td>{{ item.runMode }}</td>
                  <td><span :class="['badge', item.batchStatusType]">{{ item.batchStatus }}</span></td>
                  <td>{{ item.paymentTotalCount }}</td>
                  <td>{{ item.paymentSuccessCount }}</td>
                  <td>{{ item.paymentSuccessAmount }}</td>
                  <td>{{ item.channelSuccessCount }}</td>
                  <td>{{ item.channelSuccessAmount }}</td>
                  <td>{{ item.internalSuccessCount }}</td>
                  <td>{{ item.internalSuccessAmount }}</td>
                  <td>{{ item.paymentSuccessGapCount }}</td>
                  <td>{{ item.paymentSuccessGapAmount }}</td>
                  <td>{{ item.pendingPaymentCount ?? "未采集" }}</td>
                  <td>{{ item.pendingPaymentAmount || "未采集" }}</td>
                  <td>{{ item.missingPaymentSuccessTimeCount ?? "未采集" }}</td>
                  <td>{{ item.paymentSuccessFactMismatchCount ?? "未采集" }}</td>
                  <td>{{ item.refundSuccessCount }}</td>
                  <td>{{ item.refundSuccessAmount }}</td>
                  <td>{{ item.refundFailCount ?? "未采集" }}</td>
                  <td>{{ item.refundFailAmount || "未采集" }}</td>
                  <td>{{ item.missingRefundSuccessTimeCount ?? "未采集" }}</td>
                  <td>{{ item.refundTerminalFactMismatchCount ?? "未采集" }}</td>
                  <td>{{ item.channelAbnormalCount }}</td>
                  <td>{{ item.internalAbnormalCount }}</td>
                  <td>{{ item.pendingRefundCount }}</td>
                  <td>{{ item.pendingRefundAmount }}</td>
                  <td class="flow-summary-cell">
                    <span :class="['badge', item.reconciliationReadinessType]">{{ item.reconciliationReadinessStatus }}</span>
                    <div class="detail-hint">{{ item.reconciliationReadinessSummary }}</div>
                  </td>
                  <td class="flow-summary-cell">{{ item.summaryComment }}</td>
                  <td>{{ item.triggeredBy }}</td>
                  <td>{{ item.completedAt }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
