<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { orderApi, paymentApi } from "../api/client";
import { orderDetailRoute, paymentDetailRoute, paymentRecordDetailRoute } from "../router/detailRoutes";
import { buildAppWebCashierUrl } from "../router/externalUrls";
import { billsRoute } from "../router/listRoutes";

const router = useRouter();
const items = ref([]);
const isLoading = ref(false);
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
const loadedQuery = ref(null);
const pageSize = 20;
let latestLoadRequestId = 0;
const filters = ref({
  orderNo: "",
  serviceType: "全部",
  orderStatus: "全部"
});

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const actionRunning = computed(() => isLoading.value || Boolean(activeOrderNo.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value && loadedQuery.value && !actionRunning.value && !isSnapshotStale.value
));
const canExport = computed(() => hasLoadedData.value && total.value > 0 && !isSnapshotStale.value);
const isWriteDisabled = computed(() => !canUseSnapshot.value);

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
  if (actionRunning.value) {
    return;
  }
  filters.value = {
    orderNo: "",
    serviceType: "全部",
    orderStatus: "全部"
  };
  pageNo.value = 1;
  loadOrders();
}

function applyFilters() {
  if (actionRunning.value) {
    return;
  }
  pageNo.value = 1;
  loadOrders();
}

async function loadOrders(afterAction = false, query = buildQuery()) {
  if (actionRunning.value && !afterAction) {
    return;
  }
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  if (afterAction) {
    actionRefreshErrorMessage.value = "";
  } else {
    loadErrorMessage.value = "";
  }
  try {
    const result = await orderApi.getList(query);
    if (requestId !== latestLoadRequestId) {
      return;
    }
    items.value = result.items;
    total.value = result.total;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
    loadErrorMessage.value = "";
    actionRefreshErrorMessage.value = "";
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return;
    }
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
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function refreshOrders() {
  if (actionRunning.value) {
    return;
  }
  loadOrders(false, loadedQuery.value || buildQuery());
}

function canLaunchPayment(orderItem) {
  return orderItem?.orderStatus === "待支付";
}

function goToPage(nextPage) {
  if (!canUseSnapshot.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadOrders(false, { ...loadedQuery.value, pageNo: nextPage });
}

function exportOrders() {
  if (actionRunning.value || !canExport.value || !loadedQuery.value) {
    return;
  }
  const exportUrl = orderApi.buildExportUrl({
    orderNo: loadedQuery.value.orderNo,
    serviceType: loadedQuery.value.serviceType,
    orderStatus: loadedQuery.value.orderStatus
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

async function launchPayment(orderNo) {
  const orderItem = items.value.find((item) => item.orderNo === orderNo);
  if (isWriteDisabled.value || !canLaunchPayment(orderItem)) {
    return;
  }
  const query = { ...loadedQuery.value };
  activeOrderNo.value = orderNo;
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  launchPayload.value = null;
  try {
    const prepay = await paymentApi.prepay({
      orderNo,
      payScene: "HOME_CLEAN",
      amount: orderItem.orderAmount,
      customerName: orderItem.customerName
    });
    launchPayload.value = {
      orderNo,
      prepayOrderNo: prepay.prepayOrderNo,
      paymentOrderId: prepay.paymentOrderId,
      cashierUrl: buildAppWebCashierUrl(prepay.prepayOrderNo, undefined, prepay.payScene)
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

function isCurrentSnapshotOrder(orderItem) {
  return canUseSnapshot.value
    && items.value.some((item) => item.orderNo === orderItem?.orderNo);
}

function openSnapshotRoute(orderItem, targetRoute) {
  if (!isCurrentSnapshotOrder(orderItem) || !targetRoute) {
    return;
  }
  router.push(targetRoute);
}

function openSnapshotCashier(orderItem) {
  if (!isCurrentSnapshotOrder(orderItem) || !canOpenCashier(orderItem)) {
    return;
  }
  window.open(
    buildAppWebCashierUrl(orderItem.latestPrepayOrderNo, undefined, orderItem.latestPayScene),
    "_blank",
    "noopener,noreferrer"
  );
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
        <button class="button secondary" :disabled="actionRunning" @click="refreshOrders">
          {{ isLoading ? "刷新中..." : "刷新" }}
        </button>
        <button class="button primary" :disabled="actionRunning || !canExport" @click="exportOrders">导出订单</button>
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
          <input v-model="filters.orderNo" placeholder="请输入订单号" :disabled="actionRunning" />
        </div>
        <div class="field">
          <label>服务品类</label>
          <select v-model="filters.serviceType" :disabled="actionRunning">
            <option>全部</option>
            <option>深度保洁</option>
            <option>月嫂套餐</option>
            <option>企业保洁</option>
          </select>
        </div>
        <div class="field">
          <label>订单状态</label>
          <select v-model="filters.orderStatus" :disabled="actionRunning">
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
          <button class="button primary" :disabled="actionRunning" @click="applyFilters">
            {{ isLoading ? "查询中..." : "查询" }}
          </button>
          <button class="button secondary" :disabled="actionRunning" @click="resetFilters">重置</button>
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
              <th>支付场景</th>
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
              <td>{{ item.latestPayScene || "-" }}</td>
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
                  <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(item, orderDetailRoute(item.orderNo))">
                    查看订单详情
                  </button>
                  <button v-if="item.latestPaymentOrderId" class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(item, paymentDetailRoute(item.latestPaymentOrderId))">
                    查看支付单
                  </button>
                  <button
                    v-if="item.latestPaymentOrderId"
                    class="link-button"
                    :disabled="!canUseSnapshot"
                    @click="openSnapshotRoute(item, paymentRecordDetailRoute(item.latestPaymentOrderId))"
                  >
                    查看支付记录
                  </button>
                  <button
                    v-if="item.billNo"
                    class="link-button"
                    :disabled="!canUseSnapshot"
                    @click="openSnapshotRoute(item, billsRoute({ billNo: item.billNo, orderNo: item.orderNo }))"
                  >
                    查看账单
                  </button>
                  <button
                    v-if="canOpenCashier(item)"
                    class="link-button"
                    :disabled="!canUseSnapshot"
                    @click="openSnapshotCashier(item)"
                  >
                    打开收银台
                  </button>
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
        <button class="button secondary" :disabled="!canUseSnapshot || pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="!canUseSnapshot || pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
