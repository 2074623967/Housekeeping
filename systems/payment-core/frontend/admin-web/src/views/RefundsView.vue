<script setup>
import { onMounted, ref } from "vue";
import { refundApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeRefundOrderId = ref("");
const activeAction = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const applyForm = ref({
  paymentOrderId: "",
  refundAmount: "",
  refundMethod: "原路退款",
  refundReason: ""
});
const filters = ref({
  refundOrderId: "",
  paymentOrderId: "",
  refundStatus: "全部",
  refundMethod: "全部"
});

function isActionRunning(refundOrderId, actionName) {
  return activeRefundOrderId.value === refundOrderId && activeAction.value === actionName;
}

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

async function handleApply() {
  activeAction.value = "apply";
  actionMessage.value = "";
  try {
    const refund = await refundApi.apply({
      paymentOrderId: applyForm.value.paymentOrderId,
      refundAmount: Number(applyForm.value.refundAmount),
      refundMethod: applyForm.value.refundMethod,
      refundReason: applyForm.value.refundReason
    });
    actionMessage.value = `退款单 ${refund.refundOrderId} 已创建，等待审核。`;
    applyForm.value = {
      paymentOrderId: "",
      refundAmount: "",
      refundMethod: "原路退款",
      refundReason: ""
    };
    pageNo.value = 1;
    await loadRefunds();
  } catch (error) {
    actionMessage.value = `发起退款失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

async function runRefundAction(refundOrderId, actionName, actionLabel, actionRunner) {
  activeRefundOrderId.value = refundOrderId;
  activeAction.value = actionName;
  actionMessage.value = "";
  try {
    const refund = await actionRunner(refundOrderId);
    actionMessage.value = `退款单 ${refund.refundOrderId} 已${actionLabel}，当前状态为 ${refund.status}。`;
    await loadRefunds();
  } catch (error) {
    actionMessage.value = `退款单 ${refundOrderId} ${actionLabel}失败：${error.message}`;
  } finally {
    activeRefundOrderId.value = "";
    activeAction.value = "";
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
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>

      <div class="sub-panel">
        <div>
          <h3>发起退款</h3>
          <p>仅允许对支付成功订单发起，后端会校验累计退款金额不超过原支付金额。</p>
        </div>
        <div class="toolbar compact-toolbar">
          <div class="field">
            <label>原支付单号</label>
            <input v-model="applyForm.paymentOrderId" placeholder="PAY202607190001" />
          </div>
          <div class="field">
            <label>退款金额</label>
            <input v-model="applyForm.refundAmount" type="number" min="0.01" step="0.01" placeholder="请输入金额" />
          </div>
          <div class="field">
            <label>退款方式</label>
            <select v-model="applyForm.refundMethod">
              <option>原路退款</option>
              <option>线下打款</option>
              <option>退转付</option>
            </select>
          </div>
          <div class="field wide-field">
            <label>退款原因</label>
            <input v-model="applyForm.refundReason" placeholder="客户取消服务、服务未履约等" />
          </div>
          <div class="toolbar-actions">
            <button class="button primary" :disabled="activeAction === 'apply'" @click="handleApply">
              {{ activeAction === "apply" ? "提交中..." : "提交退款申请" }}
            </button>
          </div>
        </div>
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
            <option>REVIEWING</option>
            <option>PROCESSING</option>
            <option>SUCCESS</option>
            <option>FAIL</option>
          </select>
        </div>
        <div class="field">
          <label>退款方式</label>
          <select v-model="filters.refundMethod">
            <option>全部</option>
            <option>原路退款</option>
            <option>线下打款</option>
            <option>退转付</option>
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
                  <button
                    class="link-button"
                    :disabled="activeRefundOrderId === item.refundOrderId || item.status !== 'REVIEWING'"
                    @click="runRefundAction(item.refundOrderId, 'approve', '审核通过', refundApi.approve)"
                  >
                    {{ isActionRunning(item.refundOrderId, "approve") ? "审核中..." : "审核通过" }}
                  </button>
                  <button
                    class="link-button"
                    :disabled="activeRefundOrderId === item.refundOrderId || item.status !== 'PROCESSING'"
                    @click="runRefundAction(item.refundOrderId, 'success', '退款成功', refundApi.markSuccess)"
                  >
                    {{ isActionRunning(item.refundOrderId, "success") ? "处理中..." : "成功回调" }}
                  </button>
                  <button
                    class="link-button"
                    :disabled="activeRefundOrderId === item.refundOrderId || item.status !== 'PROCESSING'"
                    @click="runRefundAction(item.refundOrderId, 'fail', '退款失败', refundApi.markFail)"
                  >
                    {{ isActionRunning(item.refundOrderId, "fail") ? "处理中..." : "失败回调" }}
                  </button>
                  <button
                    class="link-button"
                    :disabled="activeRefundOrderId === item.refundOrderId || item.status !== 'FAIL'"
                    @click="runRefundAction(item.refundOrderId, 'retry', '重新提交', refundApi.retry)"
                  >
                    {{ isActionRunning(item.refundOrderId, "retry") ? "重试中..." : "重试" }}
                  </button>
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
