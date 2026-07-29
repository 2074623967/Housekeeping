<script setup>
import { onMounted, ref } from "vue";
import { walletApi } from "../api/client";

const dashboard = ref(null);
const loading = ref(false);
const message = ref("");

async function load() {
  loading.value = true;
  message.value = "";
  try {
    dashboard.value = await walletApi.getMarketingFundDashboard();
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel">
      <h2>营销资金台账</h2>
      <div v-if="message" style="margin-bottom:12px;color:#1d4ed8">{{ message }}</div>
      <div v-if="loading">加载中...</div>
      <div v-else-if="dashboard" style="display:grid;gap:16px">
        <div class="detail-grid" style="grid-template-columns:repeat(4,1fr)">
          <div class="detail-card"><div class="detail-label">营销资金账户</div><div class="detail-value">{{ dashboard.accountNo }}</div></div>
          <div class="detail-card"><div class="detail-label">账户名称</div><div class="detail-value">{{ dashboard.ownerName }}</div></div>
          <div class="detail-card"><div class="detail-label">可用余额</div><div class="detail-value">{{ dashboard.availableAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">冻结余额</div><div class="detail-value">{{ dashboard.frozenAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">红包累计申请</div><div class="detail-value">{{ dashboard.totalRedPacketAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">待审批金额</div><div class="detail-value">{{ dashboard.pendingApprovalAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">已发放金额</div><div class="detail-value">{{ dashboard.issuedAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">已驳回金额</div><div class="detail-value">{{ dashboard.rejectedAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">待审事件数</div><div class="detail-value">{{ dashboard.pendingRiskCount }}</div></div>
          <div class="detail-card"><div class="detail-label">已通过事件数</div><div class="detail-value">{{ dashboard.approvedRiskCount }}</div></div>
          <div class="detail-card"><div class="detail-label">已驳回事件数</div><div class="detail-value">{{ dashboard.rejectedRiskCount }}</div></div>
        </div>

        <div class="layout" style="grid-template-columns:1fr 1fr">
          <div class="panel">
            <h3>红包批次台账</h3>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>批次号</th><th>活动名称</th><th>金额</th><th>状态</th></tr>
                </thead>
                <tbody>
                  <tr v-for="row in dashboard.redPackets" :key="row.redPacketNo">
                    <td>{{ row.redPacketNo }}</td>
                    <td>{{ row.campaignName }}</td>
                    <td>{{ row.totalAmount }}</td>
                    <td><span class="badge">{{ row.status }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="panel">
            <h3>风控审批台账</h3>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>事件号</th><th>业务单号</th><th>状态</th><th>处理人</th></tr>
                </thead>
                <tbody>
                  <tr v-for="row in dashboard.riskEvents" :key="row.eventNo">
                    <td>{{ row.eventNo }}</td>
                    <td>{{ row.bizNo }}</td>
                    <td><span class="badge">{{ row.status }}</span></td>
                    <td>{{ row.handledBy }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="panel">
          <h3>营销资金支出台账</h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr><th>流水号</th><th>业务类型</th><th>业务单号</th><th>金额</th><th>时间</th></tr>
              </thead>
              <tbody>
                <tr v-for="row in dashboard.outLedgers" :key="row.ledgerNo">
                  <td>{{ row.ledgerNo }}</td>
                  <td>{{ row.bizType }}</td>
                  <td>{{ row.bizNo }}</td>
                  <td>{{ row.amount }}</td>
                  <td>{{ row.createdAt }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
