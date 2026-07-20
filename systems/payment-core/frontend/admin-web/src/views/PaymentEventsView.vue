<script setup>
import { onMounted, ref } from "vue";
import { paymentEventApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const activeEventNo = ref("");
const filters = ref({
  paymentOrderId: "",
  eventType: "全部",
  publishStatus: "全部",
  downstreamSystem: "全部"
});

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    eventType: "全部",
    publishStatus: "全部",
    downstreamSystem: "全部"
  };
  pageNo.value = 1;
  loadEvents();
}

function applyFilters() {
  pageNo.value = 1;
  loadEvents();
}

async function loadEvents() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentEventApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      eventType: filters.value.eventType,
      publishStatus: filters.value.publishStatus,
      downstreamSystem: filters.value.downstreamSystem,
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

async function republish(item) {
  activeEventNo.value = item.eventNo;
  actionMessage.value = "";
  try {
    const result = await paymentEventApi.republish(item.eventNo, {
      paymentOrderId: filters.value.paymentOrderId,
      eventType: filters.value.eventType,
      publishStatus: filters.value.publishStatus,
      downstreamSystem: filters.value.downstreamSystem,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    actionMessage.value = `事件 ${item.eventNo} 已重新投递。`;
  } catch (error) {
    actionMessage.value = `事件 ${item.eventNo} 重发失败：${error.message}`;
  } finally {
    activeEventNo.value = "";
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadEvents();
}

onMounted(loadEvents);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付事件出站</h2>
        <p>统一管理 payment-core 向账户、清分、结算等下游系统投递的事件边界与重发动作</p>
      </div>
      <button class="button primary">导出事件</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付事件出站数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>事件类型</label>
          <select v-model="filters.eventType">
            <option>全部</option>
            <option>PAYMENT_SUBMIT</option>
            <option>PAYMENT_PENDING</option>
            <option>PAYMENT_SUCCESS</option>
            <option>PAYMENT_CLOSED</option>
            <option>PAYMENT_EXPIRED_CLOSED</option>
          </select>
        </div>
        <div class="field">
          <label>发布状态</label>
          <select v-model="filters.publishStatus">
            <option>全部</option>
            <option>PENDING</option>
            <option>SUCCESS</option>
            <option>FAILED</option>
          </select>
        </div>
        <div class="field">
          <label>下游系统</label>
          <select v-model="filters.downstreamSystem">
            <option>全部</option>
            <option>gateway-access</option>
            <option>accounting-system</option>
            <option>clearing-system</option>
            <option>settlement-system</option>
            <option>payment-core-ops</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付事件出站数据加载中...</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付事件出站数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>事件号</th>
              <th>事件类型</th>
              <th>支付单号</th>
              <th>业务单号</th>
              <th>事件主题</th>
              <th>下游系统</th>
              <th>发布状态</th>
              <th>重试次数</th>
              <th>最近发布时间</th>
              <th>下次重试时间</th>
              <th>事件摘要</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.eventNo">
              <td>{{ item.eventNo }}</td>
              <td>{{ item.eventType }}</td>
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.bizNo }}</td>
              <td>{{ item.eventTopic }}</td>
              <td>{{ item.downstreamSystem }}</td>
              <td><span :class="['badge', item.publishStatusType]">{{ item.publishStatus }}</span></td>
              <td>{{ item.retryCount }}</td>
              <td>{{ item.lastPublishedAt || "-" }}</td>
              <td>{{ item.nextRetryAt || "-" }}</td>
              <td class="flow-summary-cell">{{ item.eventPayload }}</td>
              <td>{{ item.createdAt }}</td>
              <td>
                <button
                  class="link-button"
                  :disabled="activeEventNo === item.eventNo || item.publishStatus === 'SUCCESS'"
                  @click="republish(item)"
                >
                  {{ item.publishStatus === "SUCCESS" ? "已发布" : "重发" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条支付事件</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
