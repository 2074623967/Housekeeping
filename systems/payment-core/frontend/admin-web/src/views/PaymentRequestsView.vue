<script setup>
import { computed, onMounted, ref } from "vue";
import { paymentRequestApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const expandedRequestNo = ref("");
const filters = ref({
  requestNo: "",
  paymentOrderId: "",
  requestStatus: "全部"
});

const filteredItems = computed(() =>
  items.value.filter((requestItem) => {
    const normalizedRequestNo = filters.value.requestNo.trim();
    const normalizedPaymentOrderId = filters.value.paymentOrderId.trim();
    const matchesRequestNo = !normalizedRequestNo || requestItem.requestNo.includes(normalizedRequestNo);
    const matchesPaymentOrderId = !normalizedPaymentOrderId
      || requestItem.paymentOrderId.includes(normalizedPaymentOrderId);
    const matchesStatus = filters.value.requestStatus === "全部"
      || requestItem.requestStatus === filters.value.requestStatus;
    return matchesRequestNo && matchesPaymentOrderId && matchesStatus;
  })
);

function resetFilters() {
  filters.value = {
    requestNo: "",
    paymentOrderId: "",
    requestStatus: "全部"
  };
}

function togglePayload(requestNo) {
  expandedRequestNo.value = expandedRequestNo.value === requestNo ? "" : requestNo;
}

async function loadPaymentRequests() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    items.value = await paymentRequestApi.getList();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
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
      <button class="button primary">导出请求</button>
    </div>

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
          <label>请求状态</label>
          <select v-model="filters.requestStatus">
            <option>全部</option>
            <option>处理中</option>
            <option>等待回调</option>
            <option>成功</option>
            <option>已关闭</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="当前展示支付尝试报文，生产环境需继续脱敏和权限控制" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadPaymentRequests">刷新</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付请求数据加载中...</div>

      <div v-else-if="!filteredItems.length" class="state-box">当前暂无符合条件的支付请求</div>

      <div v-else class="table-wrap">
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
              <th>请求状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in filteredItems" :key="item.requestNo">
              <tr>
                <td>{{ item.requestNo }}</td>
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.prepayOrderNo }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.paymentMethod }}</td>
                <td>{{ item.channelCode }}</td>
                <td>{{ item.routeResult || "-" }}</td>
                <td><span :class="['badge', item.requestStatusType]">{{ item.requestStatus }}</span></td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <button class="link-button" @click="togglePayload(item.requestNo)">
                    {{ expandedRequestNo === item.requestNo ? "收起报文" : "查看报文" }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedRequestNo === item.requestNo">
                <td colspan="10">
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
    </section>
  </div>
</template>
