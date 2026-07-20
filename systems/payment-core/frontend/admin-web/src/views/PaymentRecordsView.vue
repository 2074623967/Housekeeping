<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentRecordApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const isLoading = ref(false);
const errorMessage = ref("");
const filters = ref({
  userId: "",
  businessOrderNo: "",
  paymentType: "",
  paymentStatus: "全部",
  paymentChannel: "",
  sortField: "createdAt",
  sortOrder: "desc"
});

const pageConfig = computed(() => ({
  title: route.meta.pageTitle || "统一支付记录",
  recordType: route.meta.recordType || "ALL"
}));

const columns = [
  { key: "serialNo", label: "序号", className: "col-serial" },
  { key: "paymentOrderId", label: "支付单号", hint: "支付系统唯一ID", className: "col-payment-id" },
  { key: "businessOrderNo", label: "业务订单号", hint: "业务方唯一ID", className: "col-business-order" },
  { key: "paymentRequestNo", label: "支付请求号", hint: "商户订单号", className: "col-payment-request" },
  { key: "applicationSourceId", label: "应用来源ID", className: "col-source" },
  { key: "businessLineId", label: "业务线ID", className: "col-line" },
  { key: "externalTransactionNo", label: "外部交易流水号", className: "col-external" },
  { key: "paymentGateway", label: "支付通道", className: "col-gateway" },
  { key: "paymentChannel", label: "支付渠道", className: "col-channel" },
  { key: "paymentType", label: "支付类型", className: "col-type" },
  { key: "paymentStatus", label: "支付状态", className: "col-status" },
  { key: "bankName", label: "银行名", className: "col-bank" },
  { key: "cardNo", label: "卡号", className: "col-card" },
  { key: "channelReturnCode", label: "渠道返回码", className: "col-code" },
  { key: "returnParameterType", label: "返参类型", className: "col-return-type" },
  { key: "parameterValue", label: "参数值", className: "col-parameter" },
  { key: "validityPeriod", label: "有效期", className: "col-validity" },
  { key: "paymentAmount", label: "支付金额", className: "col-amount" },
  { key: "refundCount", label: "退款次数", className: "col-refund-count" },
  { key: "refundedAmount", label: "已退金额", className: "col-refunded" },
  { key: "productName", label: "商品名称", className: "col-product" },
  { key: "userPaymentChannelId", label: "用户支付渠道唯一标识", className: "col-user-channel" },
  { key: "receivingAccount", label: "收款账号", className: "col-account" },
  { key: "notifyUrl", label: "通知地址", className: "col-notify" },
  { key: "callbackMqTopic", label: "回调MQ主题", className: "col-mq" },
  { key: "expireTime", label: "失效时间", className: "col-time" },
  { key: "createdAt", label: "创建时间", className: "col-time" },
  { key: "updatedAt", label: "更新时间", className: "col-time" },
  { key: "paidAt", label: "支付成功时间", className: "col-time" },
  { key: "userId", label: "用户ID", className: "col-user-id" }
];

async function loadRows() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentRecordApi.getList({
      recordType: pageConfig.value.recordType,
      ...filters.value,
      pageNo: pageNo.value,
      pageSize
    });
    rows.value = result.items;
    total.value = result.total;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function resetFilters() {
  filters.value = {
    userId: "",
    businessOrderNo: "",
    paymentType: "",
    paymentStatus: "全部",
    paymentChannel: "",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadRows();
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadRows();
}

function openPayment(row) {
  router.push({
    path: `/payment-records/${row.paymentOrderId}`,
    query: { recordType: pageConfig.value.recordType }
  });
}

function formatCell(row, column) {
  const value = row[column.key];
  return value === null || value === undefined || value === "" ? "—" : value;
}

watch(() => route.meta.recordType, loadRows);
onMounted(loadRows);
</script>

<template>
  <div class="prototype-page">
    <div class="prototype-titlebar">
      <h2>{{ pageConfig.title }}</h2>
    </div>

    <section class="prototype-query-panel">
      <div class="prototype-field">
        <label for="record-user-id">用户ID</label>
        <input id="record-user-id" v-model="filters.userId" placeholder="请输入用户ID" />
      </div>
      <div class="prototype-field">
        <label for="record-order-no">商户订单号</label>
        <input id="record-order-no" v-model="filters.businessOrderNo" placeholder="请输入商户订单号" />
      </div>
      <div class="prototype-field">
        <label for="record-payment-type">支付类型</label>
        <select id="record-payment-type" v-model="filters.paymentType">
          <option value="">全部</option>
          <option value="消费支付">消费支付</option>
        </select>
      </div>
      <div class="prototype-field">
        <label for="record-payment-status">支付状态</label>
        <select id="record-payment-status" v-model="filters.paymentStatus">
          <option value="全部">全部</option>
          <option value="支付成功">支付成功</option>
          <option value="支付中">支付中</option>
          <option value="已关闭">已关闭</option>
          <option value="支付失败">支付失败</option>
        </select>
      </div>
      <div class="prototype-field">
        <label for="record-payment-channel">支付渠道</label>
        <input id="record-payment-channel" v-model="filters.paymentChannel" placeholder="请输入支付渠道编码" />
      </div>
      <div class="prototype-field">
        <label for="record-sort-field">排序字段</label>
        <select id="record-sort-field" v-model="filters.sortField">
          <option value="createdAt">创建时间</option>
          <option value="paymentAmount">支付金额</option>
          <option value="paidAt">支付成功时间</option>
        </select>
      </div>
      <div class="prototype-field">
        <label for="record-sort-order">排序方向</label>
        <select id="record-sort-order" v-model="filters.sortOrder">
          <option value="desc">倒序</option>
          <option value="asc">正序</option>
        </select>
      </div>
      <div class="prototype-query-actions">
        <button class="prototype-button" type="button" @click="loadRows">查询</button>
        <button class="prototype-button" type="button" @click="resetFilters">重置</button>
      </div>
    </section>

    <div v-if="errorMessage" class="error-banner">支付记录加载失败：{{ errorMessage }}</div>
    <div v-if="isLoading" class="state-box">支付记录加载中...</div>
    <div v-else class="prototype-table-shell">
      <table class="prototype-table">
        <thead>
          <tr>
            <th v-for="column in columns" :key="column.key" :class="column.className">
              <span>{{ column.label }}</span>
              <small v-if="column.hint">{{ column.hint }}</small>
            </th>
            <th class="col-operation">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.paymentOrderId">
            <td v-for="column in columns" :key="column.key" :class="column.className">
              <span :class="{ 'prototype-status': column.key === 'paymentStatus' }">
                {{ formatCell(row, column) }}
              </span>
            </td>
            <td class="col-operation">
              <button class="prototype-link" type="button" @click="openPayment(row)">支付记录</button>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td class="prototype-empty" :colspan="columns.length + 1">暂无符合条件的支付记录</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="total > pageSize" class="prototype-pagination">
      <span>共 {{ total }} 条</span>
      <button type="button" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
      <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
      <button
        type="button"
        :disabled="pageNo >= Math.ceil(total / pageSize)"
        @click="goToPage(pageNo + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>
