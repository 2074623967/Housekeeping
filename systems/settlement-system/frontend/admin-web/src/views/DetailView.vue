<script setup>
import { ref } from "vue";
import { orderApi } from "../api/client";

const settlementNo = ref("SLT20001");
const detail = ref(null);
const loading = ref(false);
const message = ref("");

async function loadDetail() {
  loading.value = true;
  message.value = "";
  detail.value = null;
  try {
    detail.value = await orderApi.getDetail(settlementNo.value);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算详情</h2>
        <p>按单查看金额构成、审核轨迹和出款状态，支撑财务复核与问题定位</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>结算单号</label>
          <input v-model="settlementNo" placeholder="例如：SLT20001" />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="loading" @click="loadDetail">查询详情</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算详情加载中...</div>
      <div v-else-if="detail" class="detail-layout">
        <div class="summary-grid">
          <div class="summary-card">
            <strong>结算单号</strong>
            <span>{{ detail.order.settlementNo }}</span>
          </div>
          <div class="summary-card">
            <strong>结算对象</strong>
            <span>{{ detail.order.targetName }} / {{ detail.order.targetNo }}</span>
          </div>
          <div class="summary-card">
            <strong>状态概览</strong>
            <span>{{ detail.order.auditStatus }} / {{ detail.order.payoutStatus }}</span>
          </div>
          <div class="summary-card">
            <strong>实结金额</strong>
            <span class="amount">{{ detail.order.netSettleAmount }}</span>
          </div>
        </div>

        <div class="detail-section">
          <h3>金额明细</h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>明细项名称</th>
                  <th>明细项类型</th>
                  <th>金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in detail.items" :key="`${row.itemName}-${row.itemType}`">
                  <td>{{ row.itemName }}</td>
                  <td>{{ row.itemType }}</td>
                  <td class="amount" :class="{ deduct: row.itemType === '扣减' }">{{ row.amount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="detail-section">
          <h3>审核日志</h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>操作时间</th>
                  <th>操作动作</th>
                  <th>操作结果</th>
                  <th>操作人</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in detail.auditLogs" :key="`${row.createdAt}-${row.operatorName}-${row.action}`">
                  <td>{{ row.createdAt }}</td>
                  <td>{{ row.action }}</td>
                  <td>{{ row.result }}</td>
                  <td>{{ row.operatorName }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div v-else class="state-box">请输入结算单号后查询完整明细。</div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  color: #1e3a8a;
}

.detail-section h3 {
  margin: 0 0 12px;
  font-size: 18px;
}

.amount {
  color: #b45309;
  font-weight: 700;
}

.deduct {
  color: #b91c1c;
}
</style>
