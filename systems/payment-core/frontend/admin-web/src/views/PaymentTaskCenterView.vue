<script setup>
import { onMounted, ref } from "vue";
import { paymentTaskCenterApi } from "../api/client";

const overview = ref({
  expiredPaymentCount: 0,
  pendingCallbackCount: 0,
  failedEventCount: 0,
  failedRefundCount: 0,
  warningDayEndBatchCount: 0,
  focusAlerts: [],
  recentTaskRuns: []
});
const taskRuns = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeTaskCode = ref("");
const filters = ref({
  taskCode: "",
  runMode: "全部",
  taskStatus: "全部",
  severityLevel: "全部"
});

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentTaskCenterApi.getOverview();
    await loadTaskRuns();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

async function loadTaskRuns() {
  const result = await paymentTaskCenterApi.getTaskRuns({
    taskCode: filters.value.taskCode,
    runMode: filters.value.runMode,
    taskStatus: filters.value.taskStatus,
    severityLevel: filters.value.severityLevel,
    pageNo: pageNo.value,
    pageSize
  });
  taskRuns.value = result.items;
  total.value = result.total;
}

function applyFilters() {
  pageNo.value = 1;
  loadTaskRuns();
}

function resetFilters() {
  filters.value = {
    taskCode: "",
    runMode: "全部",
    taskStatus: "全部",
    severityLevel: "全部"
  };
  pageNo.value = 1;
  loadTaskRuns();
}

function goToPage(nextPage) {
  const maxPage = Math.max(1, Math.ceil(total.value / pageSize));
  if (nextPage < 1 || nextPage > maxPage) {
    return;
  }
  pageNo.value = nextPage;
  loadTaskRuns();
}

async function runTask(taskCode, taskName, actionRunner) {
  activeTaskCode.value = taskCode;
  actionMessage.value = "";
  try {
    const result = await actionRunner();
    overview.value = result.overview;
    actionMessage.value = `${taskName}执行完成：处理 ${result.processedCount} 条，成功 ${result.successCount} 条，失败 ${result.failCount} 条。`;
  } catch (error) {
    actionMessage.value = `${taskName}执行失败：${error.message}`;
  } finally {
    activeTaskCode.value = "";
  }
}

onMounted(loadOverview);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付任务中心</h2>
        <p>统一查看超时关单、失败事件重发、失败退款重试和日终告警，并承接自动调度与人工处理的统一留痕</p>
      </div>
      <button class="button secondary" @click="loadOverview">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付任务中心数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付任务中心数据加载中...</div>

      <template v-else>
        <div class="detail-card-grid">
          <div v-for="alert in overview.focusAlerts" :key="alert.alertType" class="detail-card">
            <div class="detail-label">{{ alert.alertTitle }}</div>
            <div class="detail-value">{{ alert.affectedCount }}</div>
            <div class="detail-hint">{{ alert.alertMessage }}</div>
          </div>
        </div>

        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">待超时关单</div>
            <div class="detail-value">{{ overview.expiredPaymentCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">待收口支付中</div>
            <div class="detail-value">{{ overview.pendingCallbackCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">失败事件</div>
            <div class="detail-value">{{ overview.failedEventCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">失败退款</div>
            <div class="detail-value">{{ overview.failedRefundCount }}</div>
          </div>
        </div>

        <div class="risk-banner">
          当前尚有 {{ overview.warningDayEndBatchCount }} 个日终告警批次未完全收口，建议优先处理超时支付、失败事件和失败退款后再推进对账闭环。
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>严重等级与升级口径</h3>
              <p class="meta">任务中心 V1.7 已按任务类型、失败笔数和处理规模统一严重等级，避免只要失败就一律标红</p>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>任务类型</th>
                  <th>P1 升级值班负责人</th>
                  <th>P2 纳入当班跟进</th>
                  <th>P3 正常观察</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>支付超时关单</td>
                  <td>失败 ≥ 5 或单次处理量 ≥ 30</td>
                  <td>失败 1-4 或单次处理量 10-29</td>
                  <td>无失败且处理量 &lt; 10</td>
                </tr>
                <tr>
                  <td>失败事件重发</td>
                  <td>失败 ≥ 3 或单次处理量 ≥ 10</td>
                  <td>失败 1-2 或有失败事件被成功重发</td>
                  <td>无失败事件待处理</td>
                </tr>
                <tr>
                  <td>失败退款重试</td>
                  <td>失败 ≥ 2 或单次处理量 ≥ 8</td>
                  <td>失败 1 笔或有退款被批量重试</td>
                  <td>无失败退款待处理</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="split-panels">
          <section class="panel mini">
            <div class="section-title">
              <div>
              <h3>核心自动化任务</h3>
              <p class="meta">本页触发的是正式版 V1.6 的统一操作；超时关单、失败事件重发、失败退款重试均已纳入自动调度与人工补偿双通道</p>
            </div>
            </div>

            <div class="sub-panel">
              <h3>支付超时关单</h3>
              <p>处理超过收银台失效时间但仍处于待支付/待回调状态的支付单，并写入支付关闭事件。</p>
              <button
                class="button primary"
                :disabled="activeTaskCode === 'PAYMENT_EXPIRE_CLOSE'"
                @click="runTask('PAYMENT_EXPIRE_CLOSE', '支付超时关单', paymentTaskCenterApi.runCloseExpiredPayments)"
              >
                {{ activeTaskCode === "PAYMENT_EXPIRE_CLOSE" ? "执行中..." : "执行超时关单" }}
              </button>
            </div>

            <div class="sub-panel">
              <h3>失败事件重发</h3>
              <p>针对下游账户、清分、结算未成功投递的支付事件执行统一重发，减少跨系统状态分叉。</p>
              <button
                class="button primary"
                :disabled="activeTaskCode === 'PAYMENT_EVENT_RETRY'"
                @click="runTask('PAYMENT_EVENT_RETRY', '失败事件重发', paymentTaskCenterApi.runRepublishFailedEvents)"
              >
                {{ activeTaskCode === "PAYMENT_EVENT_RETRY" ? "执行中..." : "执行事件重发" }}
              </button>
            </div>

            <div class="sub-panel">
              <h3>失败退款重试</h3>
              <p>将退款失败单重新推进到处理中状态，作为人工复核和后续渠道补退的统一入口。</p>
              <button
                class="button primary"
                :disabled="activeTaskCode === 'REFUND_FAIL_RETRY'"
                @click="runTask('REFUND_FAIL_RETRY', '失败退款重试', paymentTaskCenterApi.runRetryFailedRefunds)"
              >
                {{ activeTaskCode === "REFUND_FAIL_RETRY" ? "执行中..." : "执行退款重试" }}
              </button>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>当前运维关注点</h3>
                <p class="meta">按资深测试与运维视角，优先看会导致状态不一致的项</p>
              </div>
            </div>
            <table>
              <thead>
                <tr>
                  <th>关注项</th>
                  <th>数量</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>超时支付待关闭</td>
                  <td>{{ overview.expiredPaymentCount }}</td>
                  <td>避免过期单继续占用支付链路</td>
                </tr>
                <tr>
                  <td>支付中待收口</td>
                  <td>{{ overview.pendingCallbackCount }}</td>
                  <td>需要关注回调、查单或补单收敛</td>
                </tr>
                <tr>
                  <td>失败事件</td>
                  <td>{{ overview.failedEventCount }}</td>
                  <td>影响账务、清分、结算跨系统同步</td>
                </tr>
                <tr>
                  <td>失败退款</td>
                  <td>{{ overview.failedRefundCount }}</td>
                  <td>影响用户退款闭环与逆向资金核对</td>
                </tr>
                <tr>
                  <td>日终告警批次</td>
                  <td>{{ overview.warningDayEndBatchCount }}</td>
                  <td>为下一步对账和差错处理提供排查入口</td>
                </tr>
                <tr>
                  <td>升级判定口径</td>
                  <td>V1.7</td>
                  <td>已按任务类型、失败量和处理规模区分 P1 / P2 / P3</td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>任务执行日志</h3>
              <p class="meta">支持按任务编码、运行方式、任务状态和严重等级筛选</p>
            </div>
          </div>
          <div class="toolbar compact-toolbar">
            <div class="field">
              <label>任务编码</label>
              <input v-model="filters.taskCode" placeholder="如 PAYMENT_EXPIRE_CLOSE" />
            </div>
            <div class="field">
              <label>运行方式</label>
              <select v-model="filters.runMode">
                <option>全部</option>
                <option>AUTO</option>
                <option>MANUAL</option>
              </select>
            </div>
            <div class="field">
              <label>任务状态</label>
              <select v-model="filters.taskStatus">
                <option>全部</option>
                <option>SUCCESS</option>
                <option>WARNING</option>
              </select>
            </div>
            <div class="field">
              <label>严重等级</label>
              <select v-model="filters.severityLevel">
                <option>全部</option>
                <option>P1</option>
                <option>P2</option>
                <option>P3</option>
              </select>
            </div>
            <div class="toolbar-actions">
              <button class="button primary" @click="applyFilters">查询</button>
              <button class="button secondary" @click="resetFilters">重置</button>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>日志号</th>
                  <th>任务编码</th>
                  <th>任务名称</th>
                  <th>运行方式</th>
                  <th>任务状态</th>
                  <th>严重等级</th>
                  <th>升级状态</th>
                  <th>处理总数</th>
                  <th>成功数</th>
                  <th>失败数</th>
                  <th>执行摘要</th>
                  <th>建议动作</th>
                  <th>触发人</th>
                  <th>开始时间</th>
                  <th>完成时间</th>
                  <th>路由</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in taskRuns" :key="item.taskLogNo">
                  <td>{{ item.taskLogNo }}</td>
                  <td>{{ item.taskCode }}</td>
                  <td>{{ item.taskName }}</td>
                  <td>{{ item.runMode }}</td>
                  <td><span :class="['badge', item.taskStatusType]">{{ item.taskStatus }}</span></td>
                  <td><span :class="['badge', item.severityLevelType]">{{ item.severityLevel }}</span></td>
                  <td><span :class="['badge', item.escalationStatusType]">{{ item.escalationStatus }}</span></td>
                  <td>{{ item.processedCount }}</td>
                  <td>{{ item.successCount }}</td>
                  <td>{{ item.failCount }}</td>
                  <td class="flow-summary-cell">{{ item.summaryComment }}</td>
                  <td class="flow-summary-cell">{{ item.suggestedAction }}</td>
                  <td>{{ item.triggeredBy }}</td>
                  <td>{{ item.startedAt }}</td>
                  <td>{{ item.completedAt }}</td>
                  <td>
                    <RouterLink v-if="item.recommendedRoute" class="link-button" :to="item.recommendedRoute">
                      查看
                    </RouterLink>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pager">
            <span>共 {{ total }} 条任务日志</span>
            <template v-if="total > pageSize">
              <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
              <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
              <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
            </template>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
