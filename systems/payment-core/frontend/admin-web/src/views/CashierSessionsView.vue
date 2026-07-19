<script setup>
import { onMounted, ref } from "vue";
import { cashierSessionApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const filters = ref({
  sessionNo: "",
  orderNo: "",
  terminal: "全部",
  sessionStatus: "全部"
});

function resetFilters() {
  filters.value = {
    sessionNo: "",
    orderNo: "",
    terminal: "全部",
    sessionStatus: "全部"
  };
  loadCashierSessions();
}

function applyFilters() {
  loadCashierSessions();
}

async function loadCashierSessions() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    items.value = await cashierSessionApi.getList({
      sessionNo: filters.value.sessionNo,
      orderNo: filters.value.orderNo,
      terminal: filters.value.terminal,
      sessionStatus: filters.value.sessionStatus
    });
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadCashierSessions);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>收银台会话管理</h2>
        <p>查看预付单会话、终端来源、支付关联和失效状态，快速定位收银台异常</p>
      </div>
      <button class="button primary">导出会话</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        收银台会话加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>会话号</label>
          <input v-model="filters.sessionNo" placeholder="请输入预付单/会话号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>终端场景</label>
          <select v-model="filters.terminal">
            <option>全部</option>
            <option>H5</option>
            <option>PC</option>
            <option>APP</option>
            <option>小程序</option>
          </select>
        </div>
        <div class="field">
          <label>会话状态</label>
          <select v-model="filters.sessionStatus">
            <option>全部</option>
            <option>待支付</option>
            <option>支付中</option>
            <option>支付成功</option>
            <option>已完成</option>
            <option>已失效</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="当前已接入后端筛选，便于定位终端会话异常" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">收银台会话加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的收银台会话</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>会话号</th>
              <th>预付单号</th>
              <th>支付单号</th>
              <th>订单号</th>
              <th>客户</th>
              <th>终端</th>
              <th>金额</th>
              <th>会话状态</th>
              <th>创建时间</th>
              <th>失效时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.sessionNo">
              <td>{{ item.sessionNo }}</td>
              <td>{{ item.prepayOrderNo }}</td>
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.terminal }}</td>
              <td>{{ item.amount }}</td>
              <td><span :class="['badge', item.sessionStatusType]">{{ item.sessionStatus }}</span></td>
              <td>{{ item.createdAt }}</td>
              <td>{{ item.expiresAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
