<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentRouteApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const items = ref([]);
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

      <div v-else class="table-wrap">
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
                  <button class="link-button" @click="toggleExpanded(item.routeNo)">
                    {{ expandedRouteNo === item.routeNo ? "收起详情" : "展开详情" }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedRouteNo === item.routeNo">
                <td colspan="10">
                  <div class="payload-grid">
                    <div>
                      <strong>路由上下文</strong>
                      <div class="detail-grid detail-grid-wide">
                        <div><strong>客户名称：</strong>{{ formatValue(item.customerName) }}</div>
                        <div><strong>支付金额：</strong>{{ formatValue(item.amount) }}</div>
                        <div><strong>终端：</strong>{{ formatValue(item.terminal) }}</div>
                        <div><strong>客户端 IP：</strong>{{ formatValue(item.clientIp) }}</div>
                        <div><strong>幂等键：</strong>{{ formatValue(item.idempotencyKey) }}</div>
                        <div><strong>命中渠道：</strong>{{ formatValue(item.channelCode) }}</div>
                      </div>
                      <div class="table-inline-actions">
                        <button class="button secondary" @click="openPaymentDetail(item.paymentOrderId)">查看支付单详情</button>
                        <button class="button secondary" @click="openPaymentFlows(item.paymentOrderId)">查看路由流水</button>
                        <button class="button secondary" @click="openPaymentRequests(item.paymentOrderId)">查看支付请求</button>
                        <button class="button secondary" @click="openPaymentConfig">查看路由配置</button>
                      </div>
                    </div>
                    <div>
                      <strong>最近支付请求报文</strong>
                      <pre>{{ formatPayload(item.requestPayload) }}</pre>
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
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条路由执行记录</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
