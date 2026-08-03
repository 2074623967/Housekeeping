<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentRequestApi } from "../api/client";

const route = useRoute();
const overview = ref({
  totalRequestCount: 0,
  successRequestCount: 0,
  failedRequestCount: 0,
  processingRequestCount: 0,
  waitingCallbackRequestCount: 0,
  distinctTerminalCount: 0,
  distinctChannelCount: 0,
  repeatedPaymentOrderCount: 0,
  missingResponseCount: 0,
  latestRequestAt: ""
});
const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const expandedRequestNo = ref("");
const selectedItem = ref(null);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  requestNo: route.query.requestNo || "",
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  channelCode: route.query.channelCode || "",
  terminal: route.query.terminal || "全部",
  clientIp: route.query.clientIp || "",
  requestStatus: route.query.requestStatus || "全部",
  sortField: route.query.sortField || "createdAt",
  sortOrder: route.query.sortOrder || "desc"
});

const metrics = computed(() => ({
  total: overview.value.totalRequestCount,
  successTotal: overview.value.successRequestCount,
  failedTotal: overview.value.failedRequestCount,
  terminalCount: overview.value.distinctTerminalCount
}));

function resetFilters() {
  filters.value = {
    requestNo: "",
    paymentOrderId: "",
    orderNo: "",
    channelCode: "",
    terminal: "全部",
    clientIp: "",
    requestStatus: "全部",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  expandedRequestNo.value = "";
  pageNo.value = 1;
  loadPaymentRequests();
}

function togglePayload(requestNo) {
  expandedRequestNo.value = expandedRequestNo.value === requestNo ? "" : requestNo;
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadPaymentRequests() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentRequestApi.getOverview({
      requestNo: filters.value.requestNo,
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      channelCode: filters.value.channelCode,
      terminal: filters.value.terminal,
      clientIp: filters.value.clientIp,
      requestStatus: filters.value.requestStatus,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder
    });
    const result = await paymentRequestApi.getList({
      requestNo: filters.value.requestNo,
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      channelCode: filters.value.channelCode,
      terminal: filters.value.terminal,
      clientIp: filters.value.clientIp,
      requestStatus: filters.value.requestStatus,
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

function applyFilters() {
  expandedRequestNo.value = "";
  pageNo.value = 1;
  loadPaymentRequests();
}

function exportRequests() {
  const exportUrl = paymentRequestApi.buildExportUrl({
    requestNo: filters.value.requestNo,
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    channelCode: filters.value.channelCode,
    terminal: filters.value.terminal,
    clientIp: filters.value.clientIp,
    requestStatus: filters.value.requestStatus,
    sortField: filters.value.sortField,
    sortOrder: filters.value.sortOrder
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  expandedRequestNo.value = "";
  loadPaymentRequests();
}

onMounted(loadPaymentRequests);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付请求管理</h2>
        <p>查看支付尝试的请求报文、响应报文、渠道和路由结果，支撑联调与问题定位</p>
      </div>
      <button class="button primary" @click="exportRequests">导出请求</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">请求总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">请求成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">请求失败</p>
        <p class="card-value">{{ metrics.failedTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">涉及终端数</p>
        <p class="card-value">{{ metrics.terminalCount }}</p>
      </article>
    </section>

    <section class="panel">
      <div class="detail-card-grid">
        <div class="detail-card">
          <div class="detail-label">处理中请求</div>
          <div class="detail-value">{{ overview.processingRequestCount }}</div>
          <div class="detail-hint">已发起但尚未拿到最终结果的请求</div>
        </div>
        <div class="detail-card">
          <div class="detail-label">等待回调</div>
          <div class="detail-value">{{ overview.waitingCallbackRequestCount }}</div>
          <div class="detail-hint">建议联查主动查单、回调日志与支付单状态</div>
        </div>
        <div class="detail-card">
          <div class="detail-label">重复支付单请求</div>
          <div class="detail-value">{{ overview.repeatedPaymentOrderCount }}</div>
          <div class="detail-hint">用于识别重复提交、终端切换或幂等复用场景</div>
        </div>
        <div class="detail-card">
          <div class="detail-label">缺响应报文</div>
          <div class="detail-value">{{ overview.missingResponseCount }}</div>
          <div class="detail-hint">优先排查渠道超时、网关中断或序列化失败</div>
        </div>
        <div class="detail-card">
          <div class="detail-label">涉及渠道数</div>
          <div class="detail-value">{{ overview.distinctChannelCount }}</div>
          <div class="detail-hint">当前筛选范围内命中的渠道广度</div>
        </div>
        <div class="detail-card">
          <div class="detail-label">最近请求时间</div>
          <div class="detail-value">{{ overview.latestRequestAt || "-" }}</div>
          <div class="detail-hint">用于确认当前问题是否仍在持续发生</div>
        </div>
      </div>

      <div class="risk-banner">
        当前筛选范围内共 {{ overview.totalRequestCount }} 条请求，其中等待回调 {{ overview.waitingCallbackRequestCount }} 条、重复支付单请求 {{ overview.repeatedPaymentOrderCount }} 条、缺响应报文 {{ overview.missingResponseCount }} 条，建议优先联查支付单详情、路由结果、支付流水和处理日志。
      </div>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付请求数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>请求编号</label>
          <input v-model="filters.requestNo" placeholder="请输入支付请求编号" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" placeholder="如 wx_h5 / alipay_h5" />
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
          <label>客户端 IP</label>
          <input v-model="filters.clientIp" placeholder="如 127.0.0.1" />
        </div>
        <div class="field">
          <label>请求状态</label>
          <select v-model="filters.requestStatus">
            <option>全部</option>
            <option>请求已发起</option>
            <option>请求成功</option>
            <option>请求失败</option>
            <option>已关闭</option>
          </select>
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="channelCode">渠道编码</option>
            <option value="terminal">终端</option>
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
          <input value="已支持订单号、渠道、终端、IP 筛选与排序，并对 IP、幂等键和敏感报文字段做基础脱敏；后续继续补权限分域控制" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付请求数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付请求</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>请求编号</th>
                <th>支付单号</th>
                <th>预付单号</th>
                <th>订单号</th>
                <th>支付方式</th>
                <th>渠道编码</th>
                <th>路由结果</th>
                <th>终端</th>
                <th>客户端 IP</th>
                <th>幂等键</th>
                <th>请求状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in items" :key="item.requestNo">
                <tr>
                  <td>{{ item.requestNo }}</td>
                  <td>
                    <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                      {{ item.paymentOrderId }}
                    </RouterLink>
                  </td>
                  <td>{{ item.prepayOrderNo }}</td>
                  <td>{{ item.orderNo }}</td>
                  <td>{{ item.paymentMethod }}</td>
                  <td>{{ item.channelCode }}</td>
                  <td>{{ item.routeResult || "-" }}</td>
                  <td>{{ item.terminal || "-" }}</td>
                  <td>{{ item.clientIp || "-" }}</td>
                  <td>{{ item.idempotencyKey || "-" }}</td>
                  <td><span :class="['badge', item.requestStatusType]">{{ item.requestStatus }}</span></td>
                  <td>{{ item.createdAt }}</td>
                  <td>
                    <button class="link-button" @click="pickItem(item)">查看详情</button>
                    <button class="link-button" @click="togglePayload(item.requestNo)">
                      {{ expandedRequestNo === item.requestNo ? "收起报文" : "查看报文" }}
                    </button>
                  </td>
                </tr>
                <tr v-if="expandedRequestNo === item.requestNo">
                  <td colspan="13">
                    <div class="payload-grid">
                      <div>
                        <strong>请求报文</strong>
                        <pre>{{ item.requestPayload }}</pre>
                      </div>
                      <div>
                        <strong>响应报文</strong>
                        <pre>{{ item.responsePayload || "-" }}</pre>
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
              <h3>请求详情</h3>
              <span class="meta">{{ selectedItem.requestNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>预付单号</span><strong>{{ selectedItem.prepayOrderNo }}</strong></div>
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>支付方式</span><strong>{{ selectedItem.paymentMethod }}</strong></div>
              <div class="detail-card"><span>渠道编码</span><strong>{{ selectedItem.channelCode }}</strong></div>
              <div class="detail-card"><span>终端</span><strong>{{ selectedItem.terminal || "-" }}</strong></div>
              <div class="detail-card"><span>客户端 IP</span><strong>{{ selectedItem.clientIp || "-" }}</strong></div>
              <div class="detail-card"><span>请求状态</span><strong>{{ selectedItem.requestStatus }}</strong></div>
              <div class="detail-card detail-card-wide"><span>幂等键</span><strong class="mono-text">{{ selectedItem.idempotencyKey || "-" }}</strong></div>
              <div class="detail-card detail-card-wide"><span>路由结果</span><strong>{{ selectedItem.routeResult || "-" }}</strong></div>
            </div>
            <div class="ops-card">
            <div class="ops-title">联查建议</div>
              <div class="ops-row"><span>排查入口</span><span>支付单详情 / 路由结果 / 处理日志</span></div>
              <div class="ops-row"><span>重点核对</span><span>幂等键、终端、渠道编码、请求状态</span></div>
              <div class="ops-row"><span>典型场景</span><span>重复提交、渠道超时、终端切换导致的重试、缺响应报文</span></div>
            </div>
            <div class="table-inline-actions">
              <RouterLink class="link-button" :to="`/payments/${selectedItem.paymentOrderId}`">查看支付单</RouterLink>
              <RouterLink class="link-button" :to="`/payment-flows?paymentOrderId=${selectedItem.paymentOrderId}`">查看支付流水</RouterLink>
              <RouterLink class="link-button" :to="`/cashier-sessions?paymentOrderId=${selectedItem.paymentOrderId}`">查看会话</RouterLink>
              <RouterLink class="link-button" :to="`/payment-routes?paymentOrderId=${selectedItem.paymentOrderId}`">查看路由结果</RouterLink>
              <RouterLink class="link-button" :to="`/payment-logs?paymentOrderId=${selectedItem.paymentOrderId}`">查看处理日志</RouterLink>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付请求后，可在这里查看详情与联查建议。</div>
        </aside>
      </div>
      <div class="pager">
        <span>共 {{ total }} 条支付请求</span>
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
  word-break: break-all;
}
</style>
