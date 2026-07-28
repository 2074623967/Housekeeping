<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentIssueApi } from "../api/client";

const route = useRoute();
const items = ref([]);
const selectedItem = ref(null);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const isLoading = ref(true);
const activeAlertNo = ref("");
const errorMessage = ref("");
const successMessage = ref("");
const ackOperator = ref("payment-core-admin");
const filters = ref({
  alertNo: route.query.alertNo || "",
  issueNo: route.query.issueNo || "",
  paymentOrderId: route.query.paymentOrderId || "",
  alertChannel: route.query.alertChannel || "全部",
  alertStatus: route.query.alertStatus || "全部",
  ackStatus: route.query.ackStatus || "全部",
  providerDeliveryStatus: route.query.providerDeliveryStatus || "全部"
});

const metricCards = computed(() => [
  {
    title: "告警总数",
    value: total.value,
    hint: "当前筛选条件下的异常触达记录"
  },
  {
    title: "待确认回执",
    value: items.value.filter((item) => item.ackStatus === "待确认").length,
    hint: "需运营或研发确认触达闭环"
  },
  {
    title: "派发失败",
    value: items.value.filter((item) => item.alertStatus === "派发失败").length,
    hint: "需优先处理供应商或配置异常"
  },
  {
    title: "高等级告警",
    value: items.value.filter((item) => item.severity === "P1" || item.severity === "高").length,
    hint: "用于识别阻断级支付异常"
  }
]);

const alertSuggestions = computed(() => {
  if (!selectedItem.value) {
    return [];
  }
  const suggestions = [];
  if (selectedItem.value.ackStatus === "待确认") {
    suggestions.push("当前告警仍待确认，建议先核对接收人是否已收到通知并补录确认回执。");
  }
  if (selectedItem.value.alertStatus === "派发失败") {
    suggestions.push("当前告警派发失败，建议优先检查供应商配置、模板编码和通知器装配状态。");
  }
  if (selectedItem.value.providerDeliveryStatus === "CONFIG_MISSING") {
    suggestions.push("供应商配置缺失，建议先到支付配置中心检查告警供应商与端点别名。");
  }
  if (selectedItem.value.providerDeliveryStatus === "SEND_EXCEPTION") {
    suggestions.push("供应商发送异常，建议联查支付处理日志与 outbox 派发日志。");
  }
  if (!suggestions.length) {
    suggestions.push("当前告警链路已相对完整，可结合支付单详情与异常中心继续做业务复盘。");
  }
  return suggestions;
});

function resetFilters() {
  filters.value = {
    alertNo: "",
    issueNo: "",
    paymentOrderId: "",
    alertChannel: "全部",
    alertStatus: "全部",
    ackStatus: "全部",
    providerDeliveryStatus: "全部"
  };
  pageNo.value = 1;
  loadAlertLogs();
}

function applyFilters() {
  pageNo.value = 1;
  loadAlertLogs();
}

async function loadAlertLogs() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentIssueApi.getAlertLogs({
      alertNo: filters.value.alertNo,
      issueNo: filters.value.issueNo,
      paymentOrderId: filters.value.paymentOrderId,
      alertChannel: filters.value.alertChannel,
      alertStatus: filters.value.alertStatus,
      ackStatus: filters.value.ackStatus,
      providerDeliveryStatus: filters.value.providerDeliveryStatus,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items[0] || null;
  } catch (error) {
    errorMessage.value = `异常告警明细加载失败：${error.message}`;
  } finally {
    isLoading.value = false;
  }
}

async function acknowledgeAlert(alertNo) {
  if (!ackOperator.value.trim()) {
    errorMessage.value = "请先输入确认人";
    return;
  }
  activeAlertNo.value = alertNo;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    const updatedRow = await paymentIssueApi.acknowledgeAlert(alertNo, {
      operator: ackOperator.value.trim()
    });
    items.value = items.value.map((item) => (item.alertNo === alertNo ? updatedRow : item));
    if (selectedItem.value?.alertNo === alertNo) {
      selectedItem.value = updatedRow;
    }
    successMessage.value = `告警 ${alertNo} 已确认回执`;
  } catch (error) {
    errorMessage.value = `确认回执失败：${error.message}`;
  } finally {
    activeAlertNo.value = "";
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadAlertLogs();
}

function pickItem(item) {
  selectedItem.value = item;
}

onMounted(loadAlertLogs);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>异常告警明细台</h2>
        <p>统一联查 outbox、外部通知通道、供应商投递回执和人工确认状态，方便运营、研发与测试核对异常触达闭环</p>
      </div>
      <button class="button secondary" @click="loadAlertLogs">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">{{ errorMessage }}</div>
      <div v-if="successMessage" class="success-banner">
        {{ successMessage }}
      </div>

      <div class="detail-card-grid">
        <div v-for="card in metricCards" :key="card.title" class="detail-card">
          <div class="detail-label">{{ card.title }}</div>
          <div class="detail-value">{{ card.value }}</div>
          <div class="meta">{{ card.hint }}</div>
        </div>
      </div>

      <div class="toolbar">
        <div class="field">
          <label>告警编号</label>
          <input v-model="filters.alertNo" placeholder="请输入告警编号" />
        </div>
        <div class="field">
          <label>异常编号</label>
          <input v-model="filters.issueNo" placeholder="请输入异常编号" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>告警通道</label>
          <select v-model="filters.alertChannel">
            <option>全部</option>
            <option>IN_APP_OUTBOX</option>
            <option>IM</option>
            <option>SMS</option>
            <option>EMAIL</option>
          </select>
        </div>
        <div class="field">
          <label>告警状态</label>
          <select v-model="filters.alertStatus">
            <option>全部</option>
            <option>已生成</option>
            <option>已派发</option>
            <option>部分失败</option>
            <option>派发失败</option>
          </select>
        </div>
        <div class="field">
          <label>回执状态</label>
          <select v-model="filters.ackStatus">
            <option>全部</option>
            <option>待确认</option>
            <option>已确认</option>
            <option>无需回执</option>
          </select>
        </div>
        <div class="field">
          <label>供应商投递状态</label>
          <select v-model="filters.providerDeliveryStatus">
            <option>全部</option>
            <option>ACCEPTED</option>
            <option>DELIVERED</option>
            <option>CONFIG_MISSING</option>
            <option>NOTIFIER_MISSING</option>
            <option>SEND_EXCEPTION</option>
          </select>
        </div>
        <div class="field">
          <label>默认确认人</label>
          <input v-model="ackOperator" placeholder="请输入确认人" />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">异常告警明细加载中...</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的异常告警明细</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>告警编号</th>
                <th>异常编号</th>
                <th>支付单号</th>
                <th>异常类型</th>
                <th>严重等级</th>
                <th>责任组</th>
                <th>通道/接收人</th>
                <th>告警状态</th>
                <th>回执状态</th>
                <th>供应商信息</th>
                <th>投递回执</th>
                <th>操作</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.alertNo">
                <td>
                  <button class="table-link" @click="pickItem(item)">{{ item.alertNo }}</button>
                </td>
                <td>{{ item.issueNo }}</td>
                <td>
                  <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                    {{ item.paymentOrderId }}
                  </RouterLink>
                </td>
                <td>{{ item.issueType }}</td>
                <td>{{ item.severity }}</td>
                <td>{{ item.responsibilityGroup }}</td>
                <td>
                  <div>{{ item.alertChannel }}</div>
                  <div class="muted-text">{{ item.receiver }}</div>
                </td>
                <td><span :class="['badge', item.alertStatusType]">{{ item.alertStatus }}</span></td>
                <td><span :class="['badge', item.ackStatusType]">{{ item.ackStatus }}</span></td>
                <td class="flow-summary-cell">
                  <div>{{ item.providerName || item.providerCode || "-" }}</div>
                  <div class="muted-text">{{ item.endpointAlias || "-" }}</div>
                  <div class="muted-text">{{ item.templateCode || "-" }}</div>
                </td>
                <td class="flow-summary-cell">
                  <div>{{ item.providerDeliveryStatus || "-" }}</div>
                  <div class="muted-text">{{ item.providerReceiptNo || "-" }}</div>
                  <div class="muted-text">{{ item.providerDeliveryMessage || "-" }}</div>
                </td>
                <td>
                  <button
                    v-if="item.ackStatus === '待确认'"
                    class="button secondary"
                    :disabled="activeAlertNo === item.alertNo"
                    @click="acknowledgeAlert(item.alertNo)"
                  >
                    {{ activeAlertNo === item.alertNo ? "确认中..." : "确认回执" }}
                  </button>
                  <span v-else class="muted-text">无需操作</span>
                </td>
                <td>{{ item.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>告警快照</h3>
              <span class="meta">{{ selectedItem.alertNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>异常编号</span><strong>{{ selectedItem.issueNo }}</strong></div>
              <div class="detail-card"><span>触发来源</span><strong>{{ selectedItem.triggeredBy || "-" }}</strong></div>
              <div class="detail-card"><span>接收人</span><strong>{{ selectedItem.receiver || "-" }}</strong></div>
              <div class="detail-card"><span>供应商状态</span><strong>{{ selectedItem.providerDeliveryStatus || "-" }}</strong></div>
              <div class="detail-card"><span>回执确认</span><strong>{{ selectedItem.ackAt || "未确认" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">联查入口</div>
              <div class="list-actions">
                <RouterLink class="link-button" :to="`/payments/${selectedItem.paymentOrderId}`">查看支付单</RouterLink>
                <RouterLink class="link-button" :to="`/payment-issues?issueNo=${selectedItem.issueNo}`">查看异常中心</RouterLink>
                <RouterLink class="link-button" :to="`/payment-logs?paymentOrderId=${selectedItem.paymentOrderId}`">查看处理日志</RouterLink>
              </div>
            </div>
            <div class="ops-card">
              <div class="ops-title">告警内容</div>
              <div class="timeline-item">{{ selectedItem.alertContent }}</div>
              <div v-if="selectedItem.renderedContentSnapshot" class="timeline-item">{{ selectedItem.renderedContentSnapshot }}</div>
            </div>
            <div class="ops-card">
              <div class="ops-title">处理建议</div>
              <div v-for="item in alertSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
            <div class="ops-card">
              <div class="ops-title">供应商回执</div>
              <div class="ops-row"><span>供应商</span><span>{{ selectedItem.providerName || selectedItem.providerCode || "-" }}</span></div>
              <div class="ops-row"><span>端点</span><span>{{ selectedItem.endpointAlias || "-" }}</span></div>
              <div class="ops-row"><span>模板</span><span>{{ selectedItem.templateCode || "-" }}</span></div>
              <div class="ops-row"><span>回执号</span><span>{{ selectedItem.providerReceiptNo || "-" }}</span></div>
              <div class="timeline-item">{{ selectedItem.providerDeliveryMessage || "-" }}</div>
              <div class="timeline-item">{{ selectedItem.providerReceiptSnapshot || "-" }}</div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧告警后，可在这里查看告警快照、联查入口和处理建议。</div>
        </aside>
      </div>

      <div class="pager">
        <span>共 {{ total }} 条异常告警明细</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) 360px;
  gap: 16px;
}

.detail-side {
  display: grid;
  align-self: start;
}

.detail-stack {
  display: grid;
  gap: 16px;
}

.table-link {
  padding: 0;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
