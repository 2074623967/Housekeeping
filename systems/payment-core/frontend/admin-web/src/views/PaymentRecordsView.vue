<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentRecordApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const rows = ref([]);
const isLoading = ref(false);
const errorMessage = ref("");
const filters = ref({
  userId: "",
  businessOrderNo: "",
  paymentType: ""
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

const visibleRows = computed(() => rows.value.filter((row) => {
  const userMatches = !filters.value.userId
    || row.userId.toLowerCase().includes(filters.value.userId.trim().toLowerCase());
  const orderMatches = !filters.value.businessOrderNo
    || row.businessOrderNo.toLowerCase().includes(filters.value.businessOrderNo.trim().toLowerCase());
  const typeMatches = !filters.value.paymentType
    || row.paymentType === filters.value.paymentType;
  return userMatches && orderMatches && typeMatches;
}));

async function loadRows() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    rows.value = await paymentRecordApi.getList(pageConfig.value.recordType);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function resetFilters() {
  filters.value = { userId: "", businessOrderNo: "", paymentType: "" };
}

function openPayment(row) {
  router.push(`/payments/${row.paymentOrderId}`);
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
          <tr v-for="row in visibleRows" :key="row.paymentOrderId">
            <td v-for="column in columns" :key="column.key" :class="column.className">
              <span :class="{ 'prototype-status': column.key === 'paymentStatus' }">
                {{ formatCell(row, column) }}
              </span>
            </td>
            <td class="col-operation">
              <button class="prototype-link" type="button" @click="openPayment(row)">支付记录</button>
            </td>
          </tr>
          <tr v-if="!visibleRows.length">
            <td class="prototype-empty" :colspan="columns.length + 1">暂无符合条件的支付记录</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
