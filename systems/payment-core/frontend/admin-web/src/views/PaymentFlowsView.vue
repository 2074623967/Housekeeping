<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentFlowApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const expandedFlowNo = ref("");
const filters = ref({
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  flowType: route.query.flowType || "全部",
  channelCode: route.query.channelCode || "",
  terminal: route.query.terminal || "全部",
  businessStatus: route.query.businessStatus || "全部",
  keyword: route.query.keyword || "",
  sortField: route.query.sortField || "createdAt",
  sortOrder: route.query.sortOrder || "desc"
});

const metrics = computed(() => ({
  total: total.value,
  successTotal: items.value.filter((item) => item.businessStatusType === "success").length,
  warnTotal: items.value.filter((item) => item.businessStatusType === "warn").length,
  flowTypeCount: new Set(items.value.map((item) => item.flowType).filter(Boolean)).size
}));

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    flowType: "全部",
    channelCode: "",
    terminal: "全部",
    businessStatus: "全部",
    keyword: "",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  expandedFlowNo.value = "";
  pageNo.value = 1;
  loadPaymentFlows();
}

function applyFilters() {
  expandedFlowNo.value = "";
  pageNo.value = 1;
  loadPaymentFlows();
}

function toggleExpanded(flowNo) {
  expandedFlowNo.value = expandedFlowNo.value === flowNo ? "" : flowNo;
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadPaymentFlows() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentFlowApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      flowType: filters.value.flowType,
      channelCode: filters.value.channelCode,
      terminal: filters.value.terminal,
      businessStatus: filters.value.businessStatus,
      keyword: filters.value.keyword,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items[0] || null;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  expandedFlowNo.value = "";
  loadPaymentFlows();
}

function openPaymentDetail(paymentOrderId) {
  router.push(`/payments/${paymentOrderId}`);
}

function openPaymentRequests(paymentOrderId) {
  router.push(`/payment-requests?paymentOrderId=${paymentOrderId}`);
}

function openPaymentLogs(paymentOrderId) {
  router.push(`/payment-logs?paymentOrderId=${paymentOrderId}`);
}

function formatValue(value) {
  return value === null || value === undefined || value === "" ? "—" : value;
}

function formatPayload(value) {
  return value || "暂无原始报文";
}

function exportFlows() {
  const exportUrl = paymentFlowApi.buildExportUrl({
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    flowType: filters.value.flowType,
    channelCode: filters.value.channelCode,
    terminal: filters.value.terminal,
    businessStatus: filters.value.businessStatus,
    keyword: filters.value.keyword,
    sortField: filters.value.sortField,
    sortOrder: filters.value.sortOrder
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

onMounted(loadPaymentFlows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付流水查询</h2>
        <p>统一查看支付尝试、渠道回调、路由决策和业务事件，支撑交易链路排障</p>
      </div>
      <button class="button primary" @click="exportFlows">导出流水</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">流水总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">成功状态</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">待关注状态</p>
        <p class="card-value">{{ metrics.warnTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">流水类型数</p>
        <p class="card-value">{{ metrics.flowTypeCount }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付流水数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>流水类型</label>
          <select v-model="filters.flowType">
            <option>全部</option>
            <option>支付尝试</option>
            <option>渠道回调</option>
            <option>路由记录</option>
            <option>业务事件</option>
          </select>
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" placeholder="如 wx_h5 / alipay_app" />
        </div>
        <div class="field">
          <label>终端</label>
          <select v-model="filters.terminal">
            <option>全部</option>
            <option>H5</option>
            <option>PC</option>
            <option>APP</option>
            <option>小程序</option>
          </select>
        </div>
        <div class="field">
          <label>业务状态</label>
          <select v-model="filters.businessStatus">
            <option>全部</option>
            <option>处理中</option>
            <option>等待回调</option>
            <option>成功</option>
            <option>已关闭</option>
            <option>已接收</option>
            <option>已验签</option>
            <option>命中直连</option>
            <option>命中规则路由</option>
            <option>PAYMENT_SUCCESS</option>
            <option>PAYMENT_CLOSED</option>
          </select>
        </div>
        <div class="field">
          <label>关键字</label>
          <input v-model="filters.keyword" placeholder="支持摘要、请求报文、响应报文检索" />
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="retryCount">重试次数</option>
            <option value="flowType">流水类型</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="已支持渠道、终端、关键字、原始报文与联查动作，便于运营和研发统一排障" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付流水数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付流水数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>流水编号</th>
                <th>支付单号</th>
                <th>订单号</th>
                <th>预付单号</th>
                <th>流水类型</th>
                <th>渠道编码</th>
                <th>业务状态</th>
                <th>流水摘要</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in items" :key="item.flowNo">
                <tr>
                  <td>{{ item.flowNo }}</td>
                  <td>
                    <button class="link-button" @click="openPaymentDetail(item.paymentOrderId)">
                      {{ item.paymentOrderId }}
                    </button>
                  </td>
                  <td>{{ item.orderNo }}</td>
                  <td>{{ item.prepayOrderNo || "-" }}</td>
                  <td><span :class="['badge', item.flowTypeTag]">{{ item.flowType }}</span></td>
                  <td>{{ item.channelCode || "-" }}</td>
                  <td><span :class="['badge', item.businessStatusType]">{{ item.businessStatus }}</span></td>
                  <td class="flow-summary-cell">{{ item.summary }}</td>
                  <td>{{ item.createdAt }}</td>
                  <td>
                    <button class="link-button" @click="pickItem(item)">查看快照</button>
                    <button class="link-button" @click="toggleExpanded(item.flowNo)">
                      {{ expandedFlowNo === item.flowNo ? "收起报文" : "查看报文" }}
                    </button>
                  </td>
                </tr>
                <tr v-if="expandedFlowNo === item.flowNo">
                  <td colspan="10">
                    <div class="payload-grid">
                      <div>
                        <strong>原始请求/载荷</strong>
                        <pre>{{ formatPayload(item.requestPayload) }}</pre>
                      </div>
                      <div>
                        <strong>处理结果/响应</strong>
                        <pre>{{ formatPayload(item.responsePayload) }}</pre>
                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>链路快照</h3>
              <span class="meta">{{ selectedItem.flowNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>流水类型</span><strong>{{ formatValue(selectedItem.flowType) }}</strong></div>
              <div class="detail-card"><span>渠道编码</span><strong>{{ formatValue(selectedItem.channelCode) }}</strong></div>
              <div class="detail-card"><span>终端</span><strong>{{ formatValue(selectedItem.terminal) }}</strong></div>
              <div class="detail-card"><span>客户端 IP</span><strong>{{ formatValue(selectedItem.clientIp) }}</strong></div>
              <div class="detail-card"><span>业务状态</span><strong>{{ formatValue(selectedItem.businessStatus) }}</strong></div>
              <div class="detail-card"><span>回调类型</span><strong>{{ formatValue(selectedItem.notifyType) }}</strong></div>
              <div class="detail-card"><span>幂等键</span><strong class="mono-text">{{ formatValue(selectedItem.idempotencyKey) }}</strong></div>
              <div class="detail-card"><span>重试次数</span><strong>{{ formatValue(selectedItem.retryCount) }}</strong></div>
              <div class="detail-card detail-card-wide"><span>路由规则</span><strong>{{ formatValue(selectedItem.routeRule) }}</strong></div>
              <div class="detail-card"><span>下游系统</span><strong>{{ formatValue(selectedItem.downstreamSystem) }}</strong></div>
              <div class="detail-card"><span>事件主题</span><strong>{{ formatValue(selectedItem.eventTopic) }}</strong></div>
              <div class="detail-card"><span>发布状态</span><strong>{{ formatValue(selectedItem.publishStatus) }}</strong></div>
              <div class="detail-card"><span>创建时间</span><strong>{{ formatValue(selectedItem.createdAt) }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先联查</span><span>支付单详情 / 支付请求 / 处理日志</span></div>
              <div class="ops-row"><span>重点核对</span><span>终端、幂等键、回调类型、事件发布状态</span></div>
              <div class="ops-row"><span>典型场景</span><span>回调未收口、事件未发布、重试次数异常</span></div>
            </div>
            <div class="table-inline-actions">
              <button class="link-button" @click="openPaymentDetail(selectedItem.paymentOrderId)">查看支付单</button>
              <button class="link-button" @click="openPaymentRequests(selectedItem.paymentOrderId)">查看支付请求</button>
              <button class="link-button" @click="openPaymentLogs(selectedItem.paymentOrderId)">查看处理日志</button>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付流水后，可在这里查看链路快照与排障建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条支付流水</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 360px;
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

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.detail-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.detail-card strong {
  color: #0f172a;
  word-break: break-word;
}

.detail-card-wide {
  grid-column: 1 / -1;
}

.ops-card {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.ops-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.ops-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #dbe3f0;
}

.ops-row:last-child {
  border-bottom: 0;
}

.mono-text {
  font-family: "SFMono-Regular", Consolas, monospace;
}
</style>
