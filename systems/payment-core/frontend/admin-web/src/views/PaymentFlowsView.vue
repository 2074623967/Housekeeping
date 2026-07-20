<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentFlowApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const items = ref([]);
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
  businessStatus: route.query.businessStatus || "全部"
});

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    flowType: "全部",
    channelCode: "",
    businessStatus: "全部"
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

async function loadPaymentFlows() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentFlowApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      flowType: filters.value.flowType,
      channelCode: filters.value.channelCode,
      businessStatus: filters.value.businessStatus,
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

onMounted(loadPaymentFlows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付流水查询</h2>
        <p>统一查看支付尝试、渠道回调、路由决策和业务事件，支撑交易链路排障</p>
      </div>
      <button class="button primary">导出流水</button>
    </div>

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
          <label>当前说明</label>
          <input value="已支持渠道、状态、原始报文与联查动作，便于运营和研发统一排障" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付流水数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付流水数据</div>

      <div v-else class="table-wrap">
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
                  <button class="link-button" @click="toggleExpanded(item.flowNo)">
                    {{ expandedFlowNo === item.flowNo ? "收起详情" : "展开详情" }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedFlowNo === item.flowNo">
                <td colspan="10">
                  <div class="payload-grid">
                    <div>
                      <strong>链路详情</strong>
                      <div class="detail-grid detail-grid-wide">
                        <div><strong>流水类型：</strong>{{ formatValue(item.flowType) }}</div>
                        <div><strong>渠道编码：</strong>{{ formatValue(item.channelCode) }}</div>
                        <div><strong>终端：</strong>{{ formatValue(item.terminal) }}</div>
                        <div><strong>客户端 IP：</strong>{{ formatValue(item.clientIp) }}</div>
                        <div><strong>幂等键：</strong>{{ formatValue(item.idempotencyKey) }}</div>
                        <div><strong>回调类型：</strong>{{ formatValue(item.notifyType) }}</div>
                        <div><strong>路由规则：</strong>{{ formatValue(item.routeRule) }}</div>
                        <div><strong>下游系统：</strong>{{ formatValue(item.downstreamSystem) }}</div>
                        <div><strong>事件主题：</strong>{{ formatValue(item.eventTopic) }}</div>
                        <div><strong>发布状态：</strong>{{ formatValue(item.publishStatus) }}</div>
                        <div><strong>重试次数：</strong>{{ formatValue(item.retryCount) }}</div>
                      </div>
                      <div class="table-inline-actions">
                        <button class="button secondary" @click="openPaymentDetail(item.paymentOrderId)">查看支付单详情</button>
                        <button class="button secondary" @click="openPaymentRequests(item.paymentOrderId)">查看支付请求</button>
                        <button class="button secondary" @click="openPaymentLogs(item.paymentOrderId)">查看处理日志</button>
                      </div>
                    </div>
                    <div>
                      <strong>原始请求/载荷</strong>
                      <pre>{{ formatPayload(item.requestPayload) }}</pre>
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
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条支付流水</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
