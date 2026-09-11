<script setup>
import { computed, onMounted, ref } from "vue";
import { orderApi, paymentApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const actionResultMessage = ref("");
const launchPayload = ref(null);
const activeOrderNo = ref("");
const total = ref(0);
const pageNo = ref(1);
const loadedPageNo = ref(1);
const pageSize = 20;
const filters = ref({
  orderNo: "",
  serviceType: "全部",
  orderStatus: "全部"
});

const appWebBaseUrl = "http://127.0.0.1:5175";

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const isWriteDisabled = computed(() => (
  !hasLoadedData.value || isLoading.value || isSnapshotStale.value || Boolean(activeOrderNo.value)
));

function buildCashierUrl(prepayOrderNo) {
  return `${appWebBaseUrl}/cashier/${prepayOrderNo}`;
}

function buildQuery() {
  return {
    orderNo: filters.value.orderNo,
    serviceType: filters.value.serviceType,
    orderStatus: filters.value.orderStatus,
    pageNo: pageNo.value,
    pageSize
  };
}

function resetFilters() {
  filters.value = {
    orderNo: "",
    serviceType: "全部",
    orderStatus: "全部"
  };
  pageNo.value = 1;
  loadOrders();
}

function applyFilters() {
  pageNo.value = 1;
  loadOrders();
}

async function loadOrders(afterAction = false, query = buildQuery()) {
  isLoading.value = true;
  try {
    const result = await orderApi.getList(query);
    items.value = result.items;
    total.value = result.total;
    loadedPageNo.value = query.pageNo;
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
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
  } finally {
    isLoading.value = false;
  }
}

function canLaunchPayment(orderItem) {
  return orderItem.orderStatus === "待支付";
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadOrders();
}

function exportOrders() {
  const exportUrl = orderApi.buildExportUrl({
    orderNo: filters.value.orderNo,
    serviceType: filters.value.serviceType,
    orderStatus: filters.value.orderStatus
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

async function launchPayment(orderNo) {
  const query = buildQuery();
  activeOrderNo.value = orderNo;
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    const prepay = await paymentApi.prepay({
      orderNo,
      payScene: "HOME_CLEAN"
    });
    launchPayload.value = {
      orderNo,
      prepayOrderNo: prepay.prepayOrderNo,
      paymentOrderId: prepay.paymentOrderId,
      cashierUrl: buildCashierUrl(prepay.prepayOrderNo)
    };
    actionResultMessage.value = `订单 ${orderNo} 已生成预付单，可进入收银台继续支付。`;
    actionRefreshErrorMessage.value = "";
    await loadOrders(true, query);
  } catch (error) {
    actionErrorMessage.value = `发起支付失败：${error.message}`;
  } finally {
    activeOrderNo.value = "";
  }
}

function canOpenCashier(orderItem) {
  return Boolean(orderItem.latestPrepayOrderNo);
}

onMounted(loadOrders);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>订单中心</h2>
        <p>查看家政订单、支付状态和履约进展，并从订单侧发起支付</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" :disabled="isLoading || Boolean(activeOrderNo)" @click="loadOrders()">刷新</button>
        <button class="button primary" @click="exportOrders">导出订单</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "订单数据刷新失败，当前展示上次成功快照：" : "订单数据加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        发起支付结果已保留，但订单列表刷新失败，当前展示上次成功快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="state-banner">
        {{ actionResultMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" :disabled="Boolean(activeOrderNo)" />
        </div>
        <div class="field">
          <label>服务品类</label>
          <select v-model="filters.serviceType" :disabled="Boolean(activeOrderNo)">
            <option>全部</option>
            <option>深度保洁</option>
            <option>月嫂套餐</option>
            <option>企业保洁</option>
          </select>
        </div>
        <div class="field">
          <label>订单状态</label>
          <select v-model="filters.orderStatus" :disabled="Boolean(activeOrderNo)">
            <option>全部</option>
            <option>待支付</option>
            <option>待履约</option>
            <option>已完成</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="当前支持订单号、品类、状态筛选" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isLoading || Boolean(activeOrderNo)" @click="applyFilters">查询</button>
          <button class="button secondary" :disabled="isLoading || Boolean(activeOrderNo)" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="launchPayload" class="launch-panel">
        <div>
          <strong>最新支付发起结果</strong>
          <div>订单号：{{ launchPayload.orderNo }}</div>
          <div>预付单号：{{ launchPayload.prepayOrderNo }}</div>
          <div>支付单号：{{ launchPayload.paymentOrderId }}</div>
        </div>
        <div class="launch-actions">
          <a :href="launchPayload.cashierUrl" class="button primary" target="_blank" rel="noreferrer">打开收银台</a>
          <div class="meta-link">{{ launchPayload.cashierUrl }}</div>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">订单数据加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的订单快照</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的订单数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>订单号</th>
              <th>账单号</th>
              <th>用户</th>
              <th>服务品类</th>
              <th>服务者</th>
              <th>订单金额</th>
              <th>已付金额</th>
              <th>订单状态</th>
              <th>履约状态</th>
              <th>最近支付状态</th>
              <th>最近收银台状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.orderNo">
              <td>{{ item.orderNo }}</td>
              <td>{{ item.billNo || "-" }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.serviceType }}</td>
              <td>{{ item.workerName }}</td>
              <td>{{ item.orderAmount }}</td>
              <td>{{ item.paidAmount }}</td>
              <td><span :class="['badge', item.orderStatusType]">{{ item.orderStatus }}</span></td>
              <td><span :class="['badge', item.fulfillmentStatusType]">{{ item.fulfillmentStatus }}</span></td>
              <td>
                <span v-if="item.latestPaymentStatus" :class="['badge', item.latestPaymentStatusType]">
                  {{ item.latestPaymentStatus }}
                </span>
                <span v-else>-</span>
              </td>
              <td>
                <span v-if="item.latestCashierStatus" :class="['badge', item.latestCashierStatusType]">
                  {{ item.latestCashierStatus }}
                </span>
                <span v-else>-</span>
              </td>
              <td>{{ item.createdAt }}</td>
              <td>
                <div class="list-actions">
                  <span>{{ item.orderStatus === "待支付" ? "待发起" : "已生成支付链路" }}</span>
                  <span>{{ item.billNo ? `账单 ${item.billNo}` : "尚未生成账单" }}</span>
                  <RouterLink v-if="item.latestPaymentOrderId" class="link-button" :to="`/payments/${item.latestPaymentOrderId}`">
                    查看支付单
                  </RouterLink>
                  <RouterLink
                    v-if="item.latestPaymentOrderId"
                    class="link-button"
                    :to="`/payment-records/${item.latestPaymentOrderId}?recordType=ALL`"
                  >
                    查看支付记录
                  </RouterLink>
                  <RouterLink
                    v-if="item.billNo"
                    class="link-button"
                    :to="`/bills?billNo=${item.billNo}&orderNo=${item.orderNo}`"
                  >
                    查看账单
                  </RouterLink>
                  <a
                    v-if="canOpenCashier(item)"
                    class="link-button"
                    :href="buildCashierUrl(item.latestPrepayOrderNo)"
                    target="_blank"
                    rel="noreferrer"
                  >
                    打开收银台
                  </a>
                  <button
                    class="link-button"
                    :disabled="isWriteDisabled || !canLaunchPayment(item)"
                    @click="launchPayment(item.orderNo)"
                  >
                    {{
                      activeOrderNo === item.orderNo
                        ? "发起中..."
                        : canLaunchPayment(item)
                          ? "发起支付"
                          : "无需发起"
                    }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="hasLoadedData && total > pageSize" class="pager">
        <span>共 {{ total }} 条订单</span>
        <button class="button secondary" :disabled="pageNo === 1 || isLoading || Boolean(activeOrderNo) || isSnapshotStale" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize) || isLoading || Boolean(activeOrderNo) || isSnapshotStale" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
