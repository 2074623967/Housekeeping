<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesRefundActionSnapshot, refundApi } from "../api/client";
import { buildAppWebRefundProgressUrl } from "../router/externalUrls";
import {
  paymentDetailRoute as buildPaymentDetailRoute,
  paymentRecordDetailRoute as buildPaymentRecordDetailRoute
} from "../router/detailRoutes";
import { paymentLogsRoute as buildPaymentLogsRoute } from "../router/listRoutes";
import { canRunRefundAction, refundOperationSuggestions } from "../utils/refundAction";
import { refundAttemptStatusType } from "../utils/refundAttempt";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const actionResultMessage = ref("");
const actionRemark = ref("");
const actionOperator = ref("");
const activeAction = ref("");
let latestLoadRequestId = 0;

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const currentRefundOrderId = computed(() => typeof route.params.refundOrderId === "string"
  ? route.params.refundOrderId
  : "");
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.refundOrderId === currentRefundOrderId.value
  && !isLoading.value
  && !isSnapshotStale.value
  && !activeAction.value
));

const paymentDetailRoute = computed(() => {
  if (!detail.value?.paymentOrderId) {
    return "";
  }
  return buildPaymentDetailRoute(detail.value.paymentOrderId);
});

const paymentRecordDetailRoute = computed(() => {
  if (!detail.value?.paymentOrderId) {
    return "";
  }
  return buildPaymentRecordDetailRoute(detail.value.paymentOrderId);
});

const paymentLogRoute = computed(() => {
  if (!detail.value?.paymentOrderId) {
    return "";
  }
  return buildPaymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId });
});

const userRefundProgressUrl = computed(() => {
  if (!detail.value?.refundOrderId) {
    return "";
  }
  return buildAppWebRefundProgressUrl(
    detail.value.refundOrderId,
    undefined,
    detail.value.payScene
  );
});

const metricCards = computed(() => {
  if (!detail.value) {
    return [];
  }
  return [
    {
      title: "退款金额",
      value: detail.value.refundAmount || "-",
      hint: `原支付金额 ${detail.value.paidAmount || "-"}`
    },
    {
      title: "操作日志数",
      value: `${(detail.value.operationLogs || []).length}`,
      hint: "用于复盘人工与自动动作"
    },
    {
      title: "当前退款状态",
      value: detail.value.status || "-",
      hint: `原支付状态 ${detail.value.paymentStatus || "-"}`
    },
    {
      title: "退款方式",
      value: detail.value.refundMethod || "-",
      hint: `原支付方式 ${detail.value.paymentMethod || "-"}`
    }
  ];
});

const operationSuggestions = computed(() => refundOperationSuggestions(detail.value));

async function loadDetail(afterAction = false, refundOrderId = currentRefundOrderId.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = refundOrderId === currentRefundOrderId.value && detail.value
    ? {
      refundOrderId: detail.value.refundOrderId,
      paymentOrderId: detail.value.paymentOrderId,
      payScene: detail.value.payScene,
      businessChainId: detail.value.businessChainId,
      orderNo: detail.value.orderNo,
      customerName: detail.value.customerName,
      paymentMethod: detail.value.paymentMethod,
      refundAmount: detail.value.refundAmount,
      paidAmount: detail.value.paidAmount,
      refundMethod: detail.value.refundMethod,
      refundReason: detail.value.refundReason,
      channelCode: detail.value.channelCode,
      channelRefundNo: detail.value.channelRefundNo
    }
    : null;
  isLoading.value = true;
  if (afterAction) {
    actionRefreshErrorMessage.value = "";
  } else {
    loadErrorMessage.value = "";
  }
  try {
    const refundDetail = await refundApi.getDetail(refundOrderId);
    if (requestId !== latestLoadRequestId || refundOrderId !== currentRefundOrderId.value) {
      return;
    }
    if (!refundDetail || refundDetail.refundOrderId !== refundOrderId) {
      throw new Error("退款详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesRefundActionSnapshot(expectedSnapshot, refundDetail)) {
      throw new Error("退款详情响应为空或与当前支付单/金额快照不一致");
    }
    detail.value = refundDetail;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
  } catch (error) {
    if (requestId !== latestLoadRequestId || refundOrderId !== currentRefundOrderId.value) {
      return;
    }
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
      actionRefreshErrorMessage.value = "";
    }
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function openRelatedRoute(targetRoute) {
  if (!canUseSnapshot.value || !targetRoute) {
    return;
  }
  router.push(targetRoute);
}

function openExternalUrl(targetUrl) {
  if (!canUseSnapshot.value || !targetUrl) {
    return;
  }
  window.open(targetUrl, "_blank", "noopener,noreferrer");
}

async function runAction(actionName, actionLabel, actionRunner) {
  if (!canUseSnapshot.value || !canRunRefundAction(detail.value?.status, actionName)) {
    actionErrorMessage.value = `当前退款单不满足${actionLabel}条件。`;
    actionResultMessage.value = "";
    return;
  }
  const operator = actionOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  const refundOrderId = currentRefundOrderId.value;
  activeAction.value = actionName;
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const refundDetail = await actionRunner(
      refundOrderId, operator, actionRemark.value, detail.value
    );
    if (refundOrderId !== currentRefundOrderId.value) {
      return;
    }
    if (!matchesRefundActionSnapshot(detail.value, refundDetail)) {
      throw new Error("退款动作响应为空或与当前支付单/金额快照不一致");
    }
    actionResultMessage.value = `退款单 ${refundOrderId} 已${actionLabel}，当前状态为 ${refundDetail.status}。`;
    actionRemark.value = "";
    await loadDetail(true, refundOrderId);
  } catch (error) {
    if (refundOrderId !== currentRefundOrderId.value) {
      return;
    }
    actionErrorMessage.value = `退款单 ${refundOrderId} ${actionLabel}请求失败：${error.message}；请以刷新后的退款事实为准。`;
    await loadDetail(true, refundOrderId);
  } finally {
    activeAction.value = "";
  }
}

onMounted(loadDetail);

watch(currentRefundOrderId, (refundOrderId) => {
  detail.value = null;
  hasLoadedData.value = false;
  actionRefreshErrorMessage.value = "";
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  actionRemark.value = "";
  actionOperator.value = "";
  loadDetail(false, refundOrderId);
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>退款详情</h2>
        <p>统一查看退款原因、原支付快照、状态流转和人工/自动操作日志</p>
      </div>
      <div class="toolbar-actions">
        <RouterLink class="button secondary" to="/refunds">返回退款单</RouterLink>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentDetailRoute" @click="openRelatedRoute(paymentDetailRoute)">查看支付单详情</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRecordDetailRoute" @click="openRelatedRoute(paymentRecordDetailRoute)">查看支付记录详情</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentLogRoute" @click="openRelatedRoute(paymentLogRoute)">查看处理日志</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !userRefundProgressUrl" @click="openExternalUrl(userRefundProgressUrl)">打开用户退款进度</button>
        <button class="button secondary" :disabled="isLoading || Boolean(activeAction)" @click="loadDetail()">刷新</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "退款详情刷新失败，当前展示上次成功快照：" : "退款详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        退款动作后详情刷新失败，当前状态未经确认，展示的是上次成功详情快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="success-banner">
        {{ actionResultMessage }}
      </div>
      <div v-if="isLoading" class="state-box">退款详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的退款详情快照</div>

      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">退款单号</div>
            <div class="detail-value">{{ detail.refundOrderId }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">退款状态</div>
            <div class="detail-value">
              <span :class="['badge', detail.statusType]">{{ detail.status }}</span>
            </div>
          </div>
          <div class="detail-card">
            <div class="detail-label">退款金额</div>
            <div class="detail-value">{{ detail.refundAmount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">退款方式</div>
            <div class="detail-value">{{ detail.refundMethod }}</div>
          </div>
        </div>

        <div class="detail-card-grid detail-panel">
          <div v-for="card in metricCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <div class="split-panels">
          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>退款基础信息</h3>
                <p class="meta">财务与运营先确认退款原因、原支付单和申请时间</p>
              </div>
            </div>
            <div class="detail-grid">
              <div><strong>原支付单号：</strong>{{ detail.paymentOrderId }}</div>
              <div><strong>支付场景：</strong>{{ detail.payScene || "-" }}</div>
              <div><strong>原订单号：</strong>{{ detail.orderNo }}</div>
              <div><strong>客户名称：</strong>{{ detail.customerName }}</div>
              <div><strong>申请时间：</strong>{{ detail.appliedAt }}</div>
              <div><strong>成功时间：</strong>{{ detail.successAt }}</div>
              <div><strong>终态时间：</strong>{{ detail.terminalAt || "-" }}</div>
              <div><strong>退款原因：</strong>{{ detail.refundReason || "-" }}</div>
              <div><strong>退款渠道：</strong>{{ detail.channelCode || "-" }}</div>
              <div><strong>渠道退款号：</strong>{{ detail.channelRefundNo || "-" }}</div>
              <div><strong>渠道受理时间：</strong>{{ detail.channelSubmittedAt || "-" }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>原支付快照</h3>
                <p class="meta">确认原支付金额、方式和状态，避免误退款</p>
              </div>
            </div>
            <div class="detail-grid">
              <div><strong>原支付金额：</strong>{{ detail.paidAmount }}</div>
              <div><strong>原支付方式：</strong>{{ detail.paymentMethod }}</div>
              <div><strong>原支付状态：</strong>{{ detail.paymentStatus }}</div>
            </div>
          </section>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>联查入口</h3>
                <p class="meta">统一回到支付主链路和支付日志继续排查</p>
              </div>
            </div>
            <div class="list-actions">
              <button class="link-button" :disabled="!canUseSnapshot || !paymentDetailRoute" @click="openRelatedRoute(paymentDetailRoute)">查看支付单详情</button>
              <button class="link-button" :disabled="!canUseSnapshot || !paymentRecordDetailRoute" @click="openRelatedRoute(paymentRecordDetailRoute)">查看支付记录详情</button>
              <button class="link-button" :disabled="!canUseSnapshot || !paymentLogRoute" @click="openRelatedRoute(paymentLogRoute)">查看支付处理日志</button>
              <button class="link-button" :disabled="!canUseSnapshot || !userRefundProgressUrl" @click="openExternalUrl(userRefundProgressUrl)">打开用户退款进度</button>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>操作建议</h3>
                <p class="meta">按当前退款状态自动整理</p>
              </div>
            </div>
            <div class="suggestion-list">
              <div v-for="item in operationSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
          </section>
        </div>

        <div class="sub-panel">
          <h3>退款动作</h3>
          <p>操作备注会进入退款操作日志，便于财务、测试和研发统一复盘。</p>
          <div class="toolbar compact-toolbar">
            <div class="field">
              <label>操作人</label>
              <input v-model="actionOperator" maxlength="64" placeholder="请输入操作人" :disabled="isLoading || Boolean(activeAction) || isSnapshotStale" />
            </div>
            <div class="field wide-field">
              <label>操作备注</label>
              <input v-model="actionRemark" placeholder="请输入审核意见、失败原因或重试说明" :disabled="isLoading || Boolean(activeAction) || isSnapshotStale" />
            </div>
            <div class="toolbar-actions">
              <button class="button primary" :disabled="!canUseSnapshot || !canRunRefundAction(detail.status, 'approve')" @click="runAction('approve', '审核通过', refundApi.approve)">审核通过</button>
              <button class="button primary" :disabled="!canUseSnapshot || !canRunRefundAction(detail.status, 'query')" @click="runAction('query', '完成主动查单', (refundOrderId, operator, _remark, expectedSnapshot) => refundApi.query(refundOrderId, operator, expectedSnapshot))">主动查单</button>
              <button class="button secondary" :disabled="!canUseSnapshot || !canRunRefundAction(detail.status, 'retry')" @click="runAction('retry', '重试', refundApi.retry)">失败重试</button>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>渠道退款尝试</h3>
              <p class="meta">每轮渠道请求使用独立尝试号，保留未知态、受理事实和最终结果</p>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>退款尝试号</th>
                  <th>渠道</th>
                  <th>渠道退款号</th>
                  <th>尝试状态</th>
                  <th>创建时间</th>
                  <th>提交时间</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in (detail.attempts || [])" :key="item.refundAttemptNo">
                  <td>{{ item.refundAttemptNo }}</td>
                  <td>{{ item.channelCode || "-" }}</td>
                  <td>{{ item.channelRefundNo || "-" }}</td>
                  <td>
                    <span :class="['badge', refundAttemptStatusType(item.attemptStatusType)]">
                      {{ item.attemptStatus || "-" }}
                    </span>
                  </td>
                  <td>{{ item.createdAt || "-" }}</td>
                  <td>{{ item.submittedAt || "-" }}</td>
                  <td>{{ item.completedAt || "-" }}</td>
                </tr>
              </tbody>
            </table>
            <div v-if="!(detail.attempts || []).length" class="state-box">当前暂无渠道退款尝试</div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>退款操作日志</h3>
              <p class="meta">统一沉淀人工申请、审核、渠道回调和任务中心重试动作</p>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>日志号</th>
                  <th>动作编码</th>
                  <th>动作名称</th>
                  <th>原状态</th>
                  <th>目标状态</th>
                  <th>操作人</th>
                  <th>操作备注</th>
                  <th>操作时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.operationLogs" :key="item.logNo">
                  <td>{{ item.logNo }}</td>
                  <td>{{ item.actionCode }}</td>
                  <td>{{ item.actionName }}</td>
                  <td>{{ item.fromStatus }}</td>
                  <td>{{ item.toStatus }}</td>
                  <td>{{ item.operatorName }}</td>
                  <td class="flow-summary-cell">{{ item.operationRemark }}</td>
                  <td>{{ item.createdAt }}</td>
                </tr>
              </tbody>
            </table>
            <div v-if="!(detail.operationLogs || []).length" class="state-box">当前暂无退款操作日志</div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.suggestion-list {
  display: grid;
  gap: 10px;
}
</style>
