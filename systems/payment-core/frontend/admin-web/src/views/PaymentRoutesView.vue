<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentRouteApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const expandedRouteNo = ref("");
const filters = ref({
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  routeRule: route.query.routeRule || "",
  channelCode: route.query.channelCode || "",
  paymentMethod: route.query.paymentMethod || "全部",
  terminal: route.query.terminal || "全部",
  routeResult: route.query.routeResult || "全部",
  sortField: route.query.sortField || "createdAt",
  sortOrder: route.query.sortOrder || "desc"
});

const metrics = computed(() => ({
  total: total.value,
  successTotal: items.value.filter((item) => item.routeResultType === "success").length,
  warnTotal: items.value.filter((item) => item.routeResultType === "warn").length,
  channelCount: new Set(items.value.map((item) => item.channelCode).filter(Boolean)).size
}));

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    routeRule: "",
    channelCode: "",
    paymentMethod: "全部",
    terminal: "全部",
    routeResult: "全部",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  expandedRouteNo.value = "";
  pageNo.value = 1;
  loadPaymentRoutes();
}

function applyFilters() {
  expandedRouteNo.value = "";
  pageNo.value = 1;
  loadPaymentRoutes();
}

function toggleExpanded(routeNo) {
  expandedRouteNo.value = expandedRouteNo.value === routeNo ? "" : routeNo;
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadPaymentRoutes() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentRouteApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      routeRule: filters.value.routeRule,
      channelCode: filters.value.channelCode,
      paymentMethod: filters.value.paymentMethod,
      terminal: filters.value.terminal,
      routeResult: filters.value.routeResult,
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
  expandedRouteNo.value = "";
  loadPaymentRoutes();
}

function openPaymentDetail(paymentOrderId) {
  router.push(`/payments/${paymentOrderId}`);
}

function openPaymentFlows(paymentOrderId) {
  router.push(`/payment-flows?paymentOrderId=${paymentOrderId}&flowType=路由记录`);
}

function openPaymentRequests(paymentOrderId) {
  router.push(`/payment-requests?paymentOrderId=${paymentOrderId}`);
}

function openPaymentConfig() {
  router.push("/payment-config");
}

function formatValue(value) {
  return value === null || value === undefined || value === "" ? "—" : value;
}

function formatPayload(value) {
  return value || "暂无可展示报文";
}

onMounted(loadPaymentRoutes);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付路由执行结果</h2>
        <p>统一查看每笔支付命中的路由规则、落地渠道和请求上下文，支撑运营、研发和测试联合排障</p>
      </div>
      <button class="button primary" @click="openPaymentConfig">查看路由配置</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">路由记录总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">命中成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">需关注结果</p>
        <p class="card-value">{{ metrics.warnTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">命中渠道数</p>
        <p class="card-value">{{ metrics.channelCount }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付路由执行结果加载失败：{{ errorMessage }}
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
          <label>路由规则</label>
          <input v-model="filters.routeRule" placeholder="如 RULE_HOME_WX / amount>5000" />
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" placeholder="如 wx_h5 / offline_bank" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select v-model="filters.paymentMethod">
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行转账</option>
          </select>
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
          <label>路由结果</label>
          <select v-model="filters.routeResult">
            <option>全部</option>
            <option>微信JSAPI</option>
            <option>支付宝H5</option>
            <option>线下转账</option>
            <option>命中规则路由</option>
            <option>命中直连</option>
          </select>
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="channelCode">渠道编码</option>
            <option value="routeResult">路由结果</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付路由执行结果加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付路由执行记录</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>路由记录号</th>
                <th>支付单号</th>
                <th>订单号</th>
                <th>预付单号</th>
                <th>支付方式</th>
                <th>命中渠道</th>
                <th>路由结果</th>
                <th>路由规则</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in items" :key="item.routeNo">
                <tr>
                  <td>{{ item.routeNo }}</td>
                  <td>
                    <button class="link-button" @click="openPaymentDetail(item.paymentOrderId)">
                      {{ item.paymentOrderId }}
                    </button>
                  </td>
                  <td>{{ item.orderNo }}</td>
                  <td>{{ item.prepayOrderNo || "-" }}</td>
                  <td>{{ item.paymentMethod || "-" }}</td>
                  <td>{{ item.channelCode }}</td>
                  <td><span :class="['badge', item.routeResultType]">{{ item.routeResult }}</span></td>
                  <td class="flow-summary-cell">{{ item.routeRule }}</td>
                  <td>{{ item.createdAt }}</td>
                  <td>
                    <button class="link-button" @click="pickItem(item)">查看快照</button>
                    <button class="link-button" @click="toggleExpanded(item.routeNo)">
                      {{ expandedRouteNo === item.routeNo ? "收起报文" : "查看报文" }}
                    </button>
                  </td>
                </tr>
                <tr v-if="expandedRouteNo === item.routeNo">
                  <td colspan="10">
                    <div class="payload-grid">
                      <div>
                        <strong>最近支付请求报文</strong>
                        <pre>{{ formatPayload(item.requestPayload) }}</pre>
                      </div>
                      <div>
                        <strong>最近支付响应报文</strong>
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
              <h3>路由快照</h3>
              <span class="meta">{{ selectedItem.routeNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>订单号</span><strong>{{ selectedItem.orderNo }}</strong></div>
              <div class="detail-card"><span>预付单号</span><strong>{{ selectedItem.prepayOrderNo || "—" }}</strong></div>
              <div class="detail-card"><span>支付方式</span><strong>{{ selectedItem.paymentMethod || "—" }}</strong></div>
              <div class="detail-card"><span>命中渠道</span><strong>{{ selectedItem.channelCode }}</strong></div>
              <div class="detail-card"><span>路由结果</span><strong>{{ selectedItem.routeResult }}</strong></div>
              <div class="detail-card"><span>终端</span><strong>{{ formatValue(selectedItem.terminal) }}</strong></div>
              <div class="detail-card"><span>客户端 IP</span><strong>{{ formatValue(selectedItem.clientIp) }}</strong></div>
              <div class="detail-card detail-card-wide"><span>路由规则</span><strong>{{ formatValue(selectedItem.routeRule) }}</strong></div>
              <div class="detail-card detail-card-wide"><span>幂等键</span><strong class="mono-text">{{ formatValue(selectedItem.idempotencyKey) }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先联查</span><span>支付单详情 / 路由流水 / 支付请求 / 路由配置</span></div>
              <div class="ops-row"><span>重点核对</span><span>规则命中、渠道落点、终端与幂等键</span></div>
              <div class="ops-row"><span>典型场景</span><span>误命中兜底渠道、渠道落错、桌面/H5 终端规则偏差</span></div>
            </div>
            <div class="table-inline-actions">
              <button class="link-button" @click="openPaymentDetail(selectedItem.paymentOrderId)">查看支付单</button>
              <button class="link-button" @click="openPaymentFlows(selectedItem.paymentOrderId)">查看路由流水</button>
              <button class="link-button" @click="openPaymentRequests(selectedItem.paymentOrderId)">查看支付请求</button>
              <button class="link-button" @click="openPaymentConfig">查看路由配置</button>
            </div>
          </div>
          <div v-else class="state-box">选择左侧路由记录后，可在这里查看路由快照与排障建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条路由执行记录</span>
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
