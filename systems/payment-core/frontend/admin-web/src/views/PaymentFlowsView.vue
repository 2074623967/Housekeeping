<script setup>
import { computed, onMounted, ref } from "vue";
import { paymentFlowApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const filters = ref({
  paymentOrderId: "",
  orderNo: "",
  flowType: "全部"
});

const filteredItems = computed(() =>
  items.value.filter((flowItem) => {
    const normalizedPaymentOrderId = filters.value.paymentOrderId.trim();
    const normalizedOrderNo = filters.value.orderNo.trim();
    const matchesPaymentOrderId = !normalizedPaymentOrderId
      || flowItem.paymentOrderId.includes(normalizedPaymentOrderId);
    const matchesOrderNo = !normalizedOrderNo || flowItem.orderNo.includes(normalizedOrderNo);
    const matchesFlowType = filters.value.flowType === "全部" || flowItem.flowType === filters.value.flowType;
    return matchesPaymentOrderId && matchesOrderNo && matchesFlowType;
  })
);

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    flowType: "全部"
  };
}

async function loadPaymentFlows() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    items.value = await paymentFlowApi.getList();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
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
          <label>当前说明</label>
          <input value="当前聚合四类支付过程流水，便于联调与排障" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadPaymentFlows">刷新</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付流水数据加载中...</div>

      <div v-else-if="!filteredItems.length" class="state-box">当前暂无符合条件的支付流水数据</div>

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
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredItems" :key="item.flowNo">
              <td>{{ item.flowNo }}</td>
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.prepayOrderNo || "-" }}</td>
              <td><span :class="['badge', item.flowTypeTag]">{{ item.flowType }}</span></td>
              <td>{{ item.channelCode || "-" }}</td>
              <td><span :class="['badge', item.businessStatusType]">{{ item.businessStatus }}</span></td>
              <td class="flow-summary-cell">{{ item.summary }}</td>
              <td>{{ item.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
