<script setup>
import { onMounted, ref } from "vue";
import { refundApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  refundOrderId: "",
  paymentOrderId: "",
  refundStatus: "全部",
  refundMethod: "全部"
});

function resetFilters() {
  filters.value = {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "全部",
    refundMethod: "全部"
  };
  pageNo.value = 1;
  loadRefunds();
}

function applyFilters() {
  pageNo.value = 1;
  loadRefunds();
}

async function loadRefunds() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await refundApi.getList({
      refundOrderId: filters.value.refundOrderId,
      paymentOrderId: filters.value.paymentOrderId,
      refundStatus: filters.value.refundStatus,
      refundMethod: filters.value.refundMethod,
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
  loadRefunds();
}

onMounted(loadRefunds);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>退款单管理</h2>
        <p>查看标准退款、部分退款和已结算后退款场景</p>
      </div>
      <span class="badge warn">待处理 18 笔</span>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        退款单数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>退款单号</label>
          <input v-model="filters.refundOrderId" placeholder="请输入退款单号" />
        </div>
        <div class="field">
          <label>原支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入原支付单号" />
        </div>
        <div class="field">
          <label>退款状态</label>
          <select v-model="filters.refundStatus">
            <option>全部</option>
            <option>PROCESSING</option>
            <option>SUCCESS</option>
            <option>FAIL</option>
          </select>
        </div>
        <div class="field">
          <label>退款方式</label>
          <select v-model="filters.refundMethod">
            <option>全部</option>
            <option>原路退回</option>
            <option>线下打款</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">退款单数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的退款单数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>退款单号</th>
              <th>原支付单号</th>
              <th>原订单号</th>
              <th>用户</th>
              <th>退款金额</th>
              <th>退款方式</th>
              <th>退款状态</th>
              <th>申请时间</th>
              <th>成功时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.refundOrderId">
              <td>{{ item.refundOrderId }}</td>
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.refundAmount }}</td>
              <td>{{ item.refundMethod }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td>{{ item.appliedAt }}</td>
              <td>{{ item.successAt }}</td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>重试</span>
                  <span>退转付</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条退款单</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
