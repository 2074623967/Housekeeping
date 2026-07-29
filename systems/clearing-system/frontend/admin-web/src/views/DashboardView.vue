<script setup>
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { batchApi, eventApi, feeApi, orderApi, ruleApi } from "../api/client";

const metrics = ref({
  batchCount: 0,
  orderCount: 0,
  activeRuleCount: 0,
  feeRuleCount: 0,
  runningBatchCount: 0,
  failedBatchCount: 0,
  pendingShareCount: 0,
  eventCount: 0
});
const recentBatches = ref([]);
const recentOrders = ref([]);
const recentRules = ref([]);
const recentEvents = ref([]);
const message = ref("");
const loading = ref(false);

const actionCards = computed(() => [
  {
    title: "处理中批次",
    value: metrics.value.runningBatchCount,
    description: "优先检查处理中批次是否卡住，避免后续结算延迟。",
    route: "/batches",
    actionText: "查看批次进度"
  },
  {
    title: "失败清分",
    value: metrics.value.failedBatchCount,
    description: "关注失败批次和需重跑场景，及时补偿清分结果。",
    route: "/batches",
    actionText: "进入重跑排查"
  },
  {
    title: "启用规则",
    value: metrics.value.activeRuleCount,
    description: "核验当前生效规则与业务线是否一致，避免错误拆分。",
    route: "/rules",
    actionText: "查看规则版本"
  },
  {
    title: "事件追踪",
    value: metrics.value.eventCount,
    description: "抽查支付成功事件是否正确生成清分与分账结果。",
    route: "/events",
    actionText: "查看事件链路"
  }
]);

async function loadDashboard() {
  loading.value = true;
  message.value = "";
  try {
    const [batchResult, orderResult, ruleResult, feeResult, eventResult] = await Promise.all([
      batchApi.getList({ pageNo: 1, pageSize: 50 }),
      orderApi.getList({ pageNo: 1, pageSize: 50 }),
      ruleApi.getList({ pageNo: 1, pageSize: 50 }),
      feeApi.getList({ pageNo: 1, pageSize: 50 }),
      eventApi.getList({ pageNo: 1, pageSize: 50 })
    ]);
    recentBatches.value = batchResult.items.slice(0, 6);
    recentOrders.value = orderResult.items.slice(0, 6);
    recentRules.value = ruleResult.items.slice(0, 6);
    recentEvents.value = eventResult.items.slice(0, 6);
    metrics.value = {
      batchCount: batchResult.total,
      orderCount: orderResult.total,
      activeRuleCount: ruleResult.items.filter((item) => item.ruleStatus === "启用").length,
      feeRuleCount: feeResult.total,
      runningBatchCount: batchResult.items.filter((item) => item.batchStatus === "处理中").length,
      failedBatchCount: batchResult.items.filter((item) => item.failedOrderCount > 0 || item.batchStatus.includes("失败")).length,
      pendingShareCount: orderResult.items.filter((item) => item.clearingStatus !== "清分成功").length,
      eventCount: eventResult.total
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
        <h2>清分工作台</h2>
        <p>统一查看批次进度、规则生效、失败清分和支付事件链路，作为清分运营首页</p>
      </div>
      <div class="toolbar-actions">
        <button class="button primary" :disabled="loading" @click="loadDashboard">刷新工作台</button>
      </div>
    </div>

    <div v-if="message" class="state-box">{{ message }}</div>
    <div v-else-if="loading" class="state-box">清分工作台加载中...</div>
    <template v-else>
      <section class="card-grid metric-grid">
        <article class="card">
          <p class="card-title">清分批次数</p>
          <p class="card-value">{{ metrics.batchCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">清分结果数</p>
          <p class="card-value">{{ metrics.orderCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">启用规则数</p>
          <p class="card-value">{{ metrics.activeRuleCount }}</p>
        </article>
        <article class="card">
          <p class="card-title">费用规则数</p>
          <p class="card-value">{{ metrics.feeRuleCount }}</p>
        </article>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>核心待办</h3>
            <p>按清分运营优先级关注处理中批次、失败清分和事件链路。</p>
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
            <h3>最近批次与清分结果</h3>
            <p>左侧看批次进度，右侧看最近生成的清分单与状态。</p>
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
                    <th>订单数</th>
                    <th>失败数</th>
                    <th>总金额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentBatches" :key="row.batchNo">
                    <td>{{ row.batchNo }}</td>
                    <td><span class="badge" :class="row.batchStatusType">{{ row.batchStatus }}</span></td>
                    <td>{{ row.totalOrderCount }}</td>
                    <td class="risk-count">{{ row.failedOrderCount }}</td>
                    <td class="amount">{{ row.totalAmount }}</td>
                  </tr>
                  <tr v-if="recentBatches.length === 0">
                    <td colspan="5" class="empty-cell">暂无批次数据。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="summary-panel">
            <h4>最近清分结果</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>清分单号</th>
                    <th>支付单号</th>
                    <th>订单金额</th>
                    <th>平台收益</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentOrders" :key="row.clearingNo">
                    <td>{{ row.clearingNo }}</td>
                    <td>{{ row.paymentOrderId }}</td>
                    <td class="amount">{{ row.orderAmount }}</td>
                    <td class="amount">{{ row.platformAmount }}</td>
                    <td><span class="badge" :class="row.clearingStatusType">{{ row.clearingStatus }}</span></td>
                  </tr>
                  <tr v-if="recentOrders.length === 0">
                    <td colspan="5" class="empty-cell">暂无清分结果数据。</td>
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
            <h3>规则与事件动态</h3>
            <p>观察最近生效规则和支付事件消费情况，便于规则调整与联调核验。</p>
          </div>
        </div>
        <div class="dual-grid">
          <div class="summary-panel">
            <h4>最近规则</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>规则号</th>
                    <th>规则名称</th>
                    <th>类型</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentRules" :key="row.ruleNo">
                    <td>{{ row.ruleNo }}</td>
                    <td>{{ row.ruleName }}</td>
                    <td>{{ row.ruleType }}</td>
                    <td><span class="badge" :class="row.ruleStatusType">{{ row.ruleStatus }}</span></td>
                  </tr>
                  <tr v-if="recentRules.length === 0">
                    <td colspan="4" class="empty-cell">暂无规则数据。</td>
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
                    <td colspan="4" class="empty-cell">暂无清分事件数据。</td>
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
  color: #0284c7;
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
