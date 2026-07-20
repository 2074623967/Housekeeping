<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentLogApi } from "../api/client";

const route = useRoute();
const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  processStage: route.query.processStage || "全部",
  logLevel: route.query.logLevel || "全部",
  source: route.query.source || "",
  keyword: route.query.keyword || ""
});

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    processStage: "全部",
    logLevel: "全部",
    source: "",
    keyword: ""
  };
  pageNo.value = 1;
  loadPaymentLogs();
}

function applyFilters() {
  pageNo.value = 1;
  loadPaymentLogs();
}

async function loadPaymentLogs() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentLogApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      processStage: filters.value.processStage,
      logLevel: filters.value.logLevel,
      source: filters.value.source,
      keyword: filters.value.keyword,
      pageNo: pageNo.value,
      pageSize
    });
    total.value = result.total;
    items.value = result.items;
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
  loadPaymentLogs();
}

onMounted(loadPaymentLogs);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付处理日志</h2>
        <p>按处理阶段查看支付提交、路由、回调和业务事件日志，支撑异常定位与测试回归</p>
      </div>
      <button class="button primary">导出日志</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付处理日志加载失败：{{ errorMessage }}
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
          <label>处理阶段</label>
          <select v-model="filters.processStage">
            <option>全部</option>
            <option>支付提交</option>
            <option>支付路由</option>
            <option>渠道回调</option>
            <option>业务事件</option>
          </select>
        </div>
        <div class="field">
          <label>日志级别</label>
          <select v-model="filters.logLevel">
            <option>全部</option>
            <option>INFO</option>
            <option>WARN</option>
            <option>ERROR</option>
          </select>
        </div>
        <div class="field">
          <label>日志来源</label>
          <input v-model="filters.source" placeholder="如 wx_h5 / payment-core" />
        </div>
        <div class="field">
          <label>日志关键字</label>
          <input v-model="filters.keyword" placeholder="如 回调 / 路由 / SUCCESS" />
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="已支持订单号、来源和关键字检索，生产环境需接入检索与告警平台" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付处理日志加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付处理日志</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>日志编号</th>
              <th>支付单号</th>
              <th>订单号</th>
              <th>处理阶段</th>
              <th>级别</th>
              <th>来源</th>
              <th>日志消息</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.logNo">
              <td>{{ item.logNo }}</td>
              <td>
                <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                  {{ item.paymentOrderId }}
                </RouterLink>
              </td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.processStage }}</td>
              <td><span :class="['badge', item.logLevelType]">{{ item.logLevel }}</span></td>
              <td>{{ item.source }}</td>
              <td class="flow-summary-cell">{{ item.message }}</td>
              <td>{{ item.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条日志</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
