<script setup>
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { batchApi, eventApi, orderApi, payoutApi } from "../api/client";

const loading = ref(false);
const message = ref("");
const metrics = ref({
  batchCount: 0,
  orderCount: 0,
  payoutCount: 0,
  eventCount: 0,
  pendingAuditCount: 0,
  rejectedCount: 0,
  failedPayoutCount: 0,
  pendingEventCount: 0
});
const recentOrders = ref([]);
const riskPayouts = ref([]);
const recentEvents = ref([]);
const recentBatches = ref([]);

const actionCards = computed(() => [
  {
    title: "待审核结算单",
    value: metrics.value.pendingAuditCount,
    description: "优先处理待审核单据，避免出款阻塞",
    route: "/audit",
    actionText: "进入审核工作台"
  },
  {
    title: "驳回待修正",
    value: metrics.value.rejectedCount,
    description: "关注被退回单据，推动业务回补材料",
    route: "/orders",
    actionText: "查看退回结算单"
  },
  {
    title: "失败出款批次",
    value: metrics.value.failedPayoutCount,
    description: "失败批次需尽快补发，避免账务和体验风险",
    route: "/payouts",
    actionText: "处理失败出款"
  },
  {
    title: "事件消费追踪",
    value: metrics.value.eventCount,
    description: "检查清分事件是否成功生成结算单",
    route: "/events",
    actionText: "查看事件链路"
  }
]);

async function loadDashboard() {
  loading.value = true;
  message.value = "";
  try {
    const [batchResult, orderResult, payoutResult, eventResult] = await Promise.all([
      batchApi.getList({ pageNo: 1, pageSize: 50 }),
      orderApi.getList({ pageNo: 1, pageSize: 50 }),
      payoutApi.getList({ pageNo: 1, pageSize: 50 }),
      eventApi.getList({ pageNo: 1, pageSize: 50 })
    ]);

    recentOrders.value = orderResult.items.slice(0, 6);
    riskPayouts.value = payoutResult.items.filter((item) => item.failedCount > 0 || item.payoutStatus === "部分失败" || item.payoutStatus === "已失败").slice(0, 6);
    recentEvents.value = eventResult.items.slice(0, 6);
    recentBatches.value = batchResult.items.slice(0, 6);

    metrics.value = {
      batchCount: batchResult.total,
      orderCount: orderResult.total,
      payoutCount: payoutResult.total,
      eventCount: eventResult.total,
      pendingAuditCount: orderResult.items.filter((item) => item.auditStatus === "待审核").length,
      rejectedCount: orderResult.items.filter((item) => item.auditStatus === "已退回").length,
      failedPayoutCount: payoutResult.items.filter((item) => item.failedCount > 0 || item.payoutStatus === "部分失败" || item.payoutStatus === "已失败").length,
      pendingEventCount: eventResult.items.filter((item) => item.eventStatus !== "已消费").length
    };
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算工作台</h2>
        <p>统一查看待审核、失败出款、事件消费和批次进度，作为日常运营入口</p>
      </div>
      <div class="toolbar-actions">
        <button class="button primary" :disabled="loading" @click="loadDashboard">刷新工作台</button>
      </div>
    </div>

    <div v-if="message" class="state-box">{{ message }}</div>
    <div v-else-if="loading" class="state-box">工作台数据加载中...</div>
    <template v-else>
      <section class="card-grid metric-grid">
        <article class="card">
          <p class="card-title">结算批次数</p>
          <p class="card-value">{{ metrics.batchCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">结算单数</p>
          <p class="card-value">{{ metrics.orderCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">出款批次数</p>
          <p class="card-value">{{ metrics.payoutCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">结算事件数</p>
          <p class="card-value">{{ metrics.eventCount }}</p>
        </article>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>核心待办</h3>
            <p>按运营优先级处理审核、退回、失败出款和事件追踪</p>
          </div>
        </div>
        <div class="action-grid">
          <article v-for="item in actionCards" :key="item.title" class="action-card">
            <p class="action-title">{{ item.title }}</p>
            <p class="action-value">{{ item.value }}</p>
            <p class="action-desc">{{ item.description }}</p>
            <RouterLink class="action-link" :to="item.route">{{ item.actionText }}</RouterLink>
          </article>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>批次进度与风险出款</h3>
            <p>左侧看批次推进情况，右侧看失败或异常出款批次</p>
          </div>
        </div>
        <div class="dual-grid">
          <div class="summary-panel">
            <h4>最近批次</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>批次号</th>
                    <th>状态</th>
                    <th>总金额</th>
                    <th>审核数</th>
                    <th>出款数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentBatches" :key="row.batchNo">
                    <td>{{ row.batchNo }}</td>
                    <td><span class="badge" :class="row.batchStatusType">{{ row.batchStatus }}</span></td>
                    <td class="amount">{{ row.totalAmount }}</td>
                    <td>{{ row.auditedCount }}</td>
                    <td>{{ row.payoutCount }}</td>
                  </tr>
                  <tr v-if="recentBatches.length === 0">
                    <td colspan="5" class="empty-cell">暂无批次数据。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="summary-panel">
            <h4>风险出款批次</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>出款批次号</th>
                    <th>状态</th>
                    <th>失败笔数</th>
                    <th>成功笔数</th>
                    <th>总金额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in riskPayouts" :key="row.payoutBatchNo">
                    <td>{{ row.payoutBatchNo }}</td>
                    <td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td>
                    <td class="risk-count">{{ row.failedCount }}</td>
                    <td>{{ row.successCount }}</td>
                    <td class="amount">{{ row.totalAmount }}</td>
                  </tr>
                  <tr v-if="riskPayouts.length === 0">
                    <td colspan="5" class="empty-cell">当前没有失败出款批次。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>最近待审核与事件动态</h3>
            <p>快速掌握最近进入审核池的结算单和最新清分事件</p>
          </div>
        </div>
        <div class="dual-grid">
          <div class="summary-panel">
            <h4>最近结算单</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>结算单号</th>
                    <th>对象名称</th>
                    <th>实结金额</th>
                    <th>审核状态</th>
                    <th>结算状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentOrders" :key="row.settlementNo">
                    <td>{{ row.settlementNo }}</td>
                    <td>{{ row.targetName }}</td>
                    <td class="amount">{{ row.netSettleAmount }}</td>
                    <td><span class="badge" :class="row.auditStatusType">{{ row.auditStatus }}</span></td>
                    <td><span class="badge" :class="row.settlementStatusType">{{ row.settlementStatus }}</span></td>
                  </tr>
                  <tr v-if="recentOrders.length === 0">
                    <td colspan="5" class="empty-cell">暂无结算单数据。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="summary-panel">
            <h4>最近事件</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>事件号</th>
                    <th>业务单号</th>
                    <th>状态</th>
                    <th>摘要</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentEvents" :key="row.eventNo">
                    <td>{{ row.eventNo }}</td>
                    <td>{{ row.bizNo }}</td>
                    <td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td>
                    <td>{{ row.summary }}</td>
                  </tr>
                  <tr v-if="recentEvents.length === 0">
                    <td colspan="4" class="empty-cell">暂无结算事件数据。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.metric-grid {
  margin-bottom: 18px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.section-head h3 {
  margin: 0;
  font-size: 20px;
}

.section-head p {
  margin: 4px 0 0;
  color: #64748b;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.action-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid #dbeafe;
  background: linear-gradient(180deg, #f8fbff 0%, #eef6ff 100%);
}

.action-title {
  margin: 0;
  font-size: 14px;
  color: #475569;
}

.action-value {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
}

.action-desc {
  margin: 0;
  min-height: 40px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.action-link {
  color: #059669;
  font-weight: 600;
}

.dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.summary-panel h4 {
  margin: 0 0 12px;
  font-size: 16px;
}

.amount {
  color: #b45309;
  font-weight: 700;
}

.risk-count {
  color: #b91c1c;
  font-weight: 700;
}

.empty-cell {
  text-align: center;
  color: #64748b;
}
</style>
