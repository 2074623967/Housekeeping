<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { refundApi } from "../api/client";
import {
  paymentDetailRoute as buildPaymentDetailRoute,
  paymentRecordDetailRoute as buildPaymentRecordDetailRoute
} from "../router/detailRoutes";
import { paymentLogsRoute as buildPaymentLogsRoute } from "../router/listRoutes";
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

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
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

const operationSuggestions = computed(() => {
  if (!detail.value) {
    return [];
  }
  const suggestions = [];
  if (detail.value.status === "REVIEWING") {
    suggestions.push("当前退款单仍在审核阶段，建议先核对退款原因、原支付事实和审批备注后再放行。");
  }
  if (detail.value.status === "PROCESSING") {
    suggestions.push("当前退款单已进入处理中，建议优先关注渠道回调和任务中心自动重试结果。");
  }
  if (detail.value.status === "FAIL") {
    suggestions.push("当前退款单已失败，建议结合操作日志查看失败原因，并决定是否执行失败重试。");
  }
  if (["SUCCESS", "FAIL"].includes(detail.value.status)
    && (!detail.value.terminalAt || detail.value.terminalAt === "-")) {
    suggestions.push("当前退款单缺少本地终态时间，请核对可信回调、主动查单和最新退款尝试，勿用申请时间代填。");
  }
  if (!(detail.value.operationLogs || []).length) {
    suggestions.push("当前暂无退款操作日志，建议补充申请、审核或回调动作后再做复盘。");
  }
  if (!suggestions.length) {
    suggestions.push("当前退款状态稳定，可结合支付单详情和支付记录详情继续做资金复盘。");
  }
  return suggestions;
});

async function loadDetail(afterAction = false) {
  isLoading.value = true;
  try {
    detail.value = await refundApi.getDetail(route.params.refundOrderId);
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
  } catch (error) {
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
      actionRefreshErrorMessage.value = "";
    }
  } finally {
    isLoading.value = false;
  }
}

async function runAction(actionName, actionLabel, actionRunner) {
  const operator = actionOperator.value.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  activeAction.value = actionName;
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const refundDetail = await actionRunner(route.params.refundOrderId, operator, actionRemark.value);
    detail.value = refundDetail;
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    actionResultMessage.value = `退款单 ${route.params.refundOrderId} 已${actionLabel}，当前状态为 ${refundDetail.status}。`;
    actionRemark.value = "";
    await loadDetail(true);
  } catch (error) {
    actionErrorMessage.value = `退款单 ${route.params.refundOrderId} ${actionLabel}失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

onMounted(loadDetail);
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
        <button class="button secondary" :disabled="!paymentDetailRoute" @click="router.push(paymentDetailRoute)">查看支付单详情</button>
        <button class="button secondary" :disabled="!paymentRecordDetailRoute" @click="router.push(paymentRecordDetailRoute)">查看支付记录详情</button>
        <button class="button secondary" :disabled="!paymentLogRoute" @click="router.push(paymentLogRoute)">查看处理日志</button>
        <button class="button secondary" :disabled="isLoading || Boolean(activeAction)" @click="loadDetail()">刷新</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "退款详情刷新失败，当前展示上次成功快照：" : "退款详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        退款动作结果已更新当前详情，但详情刷新失败，当前展示动作响应快照：{{ actionRefreshErrorMessage }}
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
              <button class="link-button" :disabled="!paymentDetailRoute" @click="router.push(paymentDetailRoute)">查看支付单详情</button>
              <button class="link-button" :disabled="!paymentRecordDetailRoute" @click="router.push(paymentRecordDetailRoute)">查看支付记录详情</button>
              <button class="link-button" :disabled="!paymentLogRoute" @click="router.push(paymentLogRoute)">查看支付处理日志</button>
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
              <input v-model="actionOperator" maxlength="64" placeholder="请输入操作人" :disabled="Boolean(activeAction) || isSnapshotStale" />
            </div>
            <div class="field wide-field">
              <label>操作备注</label>
              <input v-model="actionRemark" placeholder="请输入审核意见、失败原因或重试说明" :disabled="Boolean(activeAction) || isSnapshotStale" />
            </div>
            <div class="toolbar-actions">
              <button class="button primary" :disabled="Boolean(activeAction) || isSnapshotStale || detail.status !== 'REVIEWING'" @click="runAction('approve', '审核通过', refundApi.approve)">审核通过</button>
              <button class="button primary" :disabled="Boolean(activeAction) || isSnapshotStale || detail.status !== 'PROCESSING'" @click="runAction('query', '完成主动查单', (refundOrderId, operator) => refundApi.query(refundOrderId, operator))">主动查单</button>
              <button class="button secondary" :disabled="Boolean(activeAction) || isSnapshotStale || detail.status !== 'FAIL'" @click="runAction('retry', '重试', refundApi.retry)">失败重试</button>
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
            <div v-if="!detail.operationLogs.length" class="state-box">当前暂无退款操作日志</div>
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
