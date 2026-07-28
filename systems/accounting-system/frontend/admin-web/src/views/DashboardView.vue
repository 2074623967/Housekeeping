<script setup>
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { accountApi, adjustmentApi, eventApi, freezeApi, ledgerApi, subjectApi } from "../api/client";

const metrics = ref({
  subjectTotal: 0,
  accountTotal: 0,
  ledgerTotal: 0,
  freezeTotal: 0,
  adjustmentTotal: 0,
  eventTotal: 0,
  pendingFreezeTotal: 0,
  pendingAdjustmentTotal: 0,
  failedEventTotal: 0
});
const recentAccounts = ref([]);
const recentFreezes = ref([]);
const recentAdjustments = ref([]);
const recentEvents = ref([]);
const recentLedgers = ref([]);
const message = ref("");
const loading = ref(true);

const actionCards = computed(() => [
  {
    title: "待处理冻结单",
    value: metrics.value.pendingFreezeTotal,
    description: "优先处理冻结、解冻中的资金限制动作，避免影响后续出款与退款。",
    route: "/freezes",
    actionText: "进入冻结工作台"
  },
  {
    title: "待审批调账单",
    value: metrics.value.pendingAdjustmentTotal,
    description: "关注人工调账审批和生效状态，避免账实不一致。",
    route: "/adjustments",
    actionText: "进入调账审批"
  },
  {
    title: "失败事件",
    value: metrics.value.failedEventTotal,
    description: "核对事件消费失败原因，确认是否需要补偿入账或重放。",
    route: "/events",
    actionText: "查看事件链路"
  },
  {
    title: "账务流水总量",
    value: metrics.value.ledgerTotal,
    description: "抽检最近账务流水，确认支付、清分、退款是否已入账留痕。",
    route: "/ledgers",
    actionText: "查看流水明细"
  }
]);

async function loadSummary() {
  loading.value = true;
  message.value = "";
  try {
    const [subjects, accounts, ledgers, freezes, adjustments, events] = await Promise.all([
      subjectApi.getList({ pageNo: 1, pageSize: 20 }),
      accountApi.getList({ pageNo: 1, pageSize: 20 }),
      ledgerApi.getList({ pageNo: 1, pageSize: 20 }),
      freezeApi.getList({ pageNo: 1, pageSize: 20 }),
      adjustmentApi.getList({ pageNo: 1, pageSize: 20 }),
      eventApi.getList({ pageNo: 1, pageSize: 20 })
    ]);
    recentAccounts.value = accounts.items.slice(0, 6);
    recentFreezes.value = freezes.items.slice(0, 6);
    recentAdjustments.value = adjustments.items.slice(0, 6);
    recentEvents.value = events.items.slice(0, 6);
    recentLedgers.value = ledgers.items.slice(0, 6);
    metrics.value = {
      subjectTotal: subjects.total,
      accountTotal: accounts.total,
      ledgerTotal: ledgers.total,
      freezeTotal: freezes.total,
      adjustmentTotal: adjustments.total,
      eventTotal: events.total,
      pendingFreezeTotal: freezes.items.filter((item) => item.freezeStatus !== "已解冻").length,
      pendingAdjustmentTotal: adjustments.items.filter((item) => item.adjustStatus !== "已生效").length,
      failedEventTotal: events.items.filter((item) => item.eventStatus && item.eventStatus !== "处理成功").length
    };
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadSummary);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账务工作台</h2>
        <p>统一查看账户规模、风险动作、事件消费和最近账务留痕，作为账务运营首页</p>
      </div>
      <div class="toolbar-actions">
        <button class="button primary" :disabled="loading" @click="loadSummary">刷新工作台</button>
      </div>
    </div>

    <div v-if="message" class="state-box">{{ message }}</div>
    <div v-else-if="loading" class="state-box">账务工作台加载中...</div>
    <template v-else>
      <section class="card-grid metric-grid">
        <article class="card">
          <p class="card-title">账户主体数</p>
          <p class="card-value">{{ metrics.subjectTotal }}</p>
        </article>
        <article class="card">
          <p class="card-title">账户总数</p>
          <p class="card-value">{{ metrics.accountTotal }}</p>
        </article>
        <article class="card">
          <p class="card-title">冻结 / 调账</p>
          <p class="card-value">{{ metrics.freezeTotal }} / {{ metrics.adjustmentTotal }}</p>
        </article>
        <article class="card">
          <p class="card-title">账务事件数</p>
          <p class="card-value">{{ metrics.eventTotal }}</p>
        </article>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>核心待办</h3>
            <p>按账务运营优先级关注冻结、调账审批、事件失败与流水核对。</p>
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
            <h3>账户与账务流水</h3>
            <p>左侧关注最近账户余额变化，右侧核对最近流水入账情况。</p>
          </div>
        </div>
        <div class="dual-grid">
          <div class="summary-panel">
            <h4>最近账户</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>账户号</th>
                    <th>主体</th>
                    <th>类型</th>
                    <th>可用余额</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentAccounts" :key="row.accountNo">
                    <td>{{ row.accountNo }}</td>
                    <td>{{ row.subjectName }}</td>
                    <td>{{ row.accountType }}</td>
                    <td class="amount">{{ row.availableAmount }}</td>
                    <td><span class="badge" :class="row.statusType">{{ row.accountStatus }}</span></td>
                  </tr>
                  <tr v-if="recentAccounts.length === 0">
                    <td colspan="5" class="empty-cell">暂无账户数据。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="summary-panel">
            <h4>最近流水</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>流水号</th>
                    <th>业务类型</th>
                    <th>方向</th>
                    <th>金额</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in recentLedgers" :key="row.ledgerNo">
                    <td>{{ row.ledgerNo }}</td>
                    <td>{{ row.bizType }}</td>
                    <td>{{ row.direction }}</td>
                    <td class="amount">{{ row.amount }}</td>
                    <td><span class="badge" :class="row.statusType">{{ row.ledgerStatus }}</span></td>
                  </tr>
                  <tr v-if="recentLedgers.length === 0">
                    <td colspan="5" class="empty-cell">暂无账务流水数据。</td>
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
            <h3>冻结、调账与事件动态</h3>
            <p>用于核对风险动作、审批进度和事件消费结果，支持日常运营排查。</p>
          </div>
        </div>
        <div class="triple-grid">
          <div class="summary-panel">
            <h4>最近冻结单</h4>
            <div class="mini-list">
              <div v-for="row in recentFreezes" :key="row.freezeNo" class="mini-item">
                <div>
                  <strong>{{ row.freezeNo }}</strong>
                  <p>{{ row.accountNo }}</p>
                </div>
                <span class="badge" :class="row.statusType">{{ row.freezeStatus }}</span>
              </div>
              <div v-if="recentFreezes.length === 0" class="empty-cell">暂无冻结单数据。</div>
            </div>
          </div>

          <div class="summary-panel">
            <h4>最近调账单</h4>
            <div class="mini-list">
              <div v-for="row in recentAdjustments" :key="row.adjustNo" class="mini-item">
                <div>
                  <strong>{{ row.adjustNo }}</strong>
                  <p>{{ row.accountNo }}</p>
                </div>
                <span class="badge" :class="row.statusType">{{ row.adjustStatus }}</span>
              </div>
              <div v-if="recentAdjustments.length === 0" class="empty-cell">暂无调账单数据。</div>
            </div>
          </div>

          <div class="summary-panel">
            <h4>最近事件</h4>
            <div class="mini-list">
              <div v-for="row in recentEvents" :key="row.eventNo" class="mini-item">
                <div>
                  <strong>{{ row.eventNo }}</strong>
                  <p>{{ row.summary }}</p>
                </div>
                <span class="badge" :class="row.statusType">{{ row.eventStatus }}</span>
              </div>
              <div v-if="recentEvents.length === 0" class="empty-cell">暂无事件数据。</div>
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

.section-head h3,
.summary-panel h4 {
  margin: 0;
}

.section-head p {
  margin: 4px 0 0;
  color: #64748b;
}

.action-grid,
.triple-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.triple-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.action-title,
.mini-item p {
  margin: 0;
  color: #475569;
}

.action-value {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
}

.action-desc {
  margin: 0;
  min-height: 42px;
  color: #64748b;
  line-height: 1.5;
}

.action-link {
  color: #2563eb;
  font-weight: 600;
}

.dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.summary-panel {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.mini-list {
  margin-top: 12px;
}

.mini-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed #cbd5e1;
}

.mini-item:last-child {
  border-bottom: 0;
}

.mini-item strong {
  display: block;
  margin-bottom: 4px;
}

.amount {
  font-weight: 700;
  color: #0f172a;
}

.empty-cell {
  padding: 16px 0;
  color: #64748b;
  text-align: center;
}

@media (max-width: 960px) {
  .action-grid,
  .dual-grid,
  .triple-grid {
    grid-template-columns: 1fr;
  }
}
</style>
