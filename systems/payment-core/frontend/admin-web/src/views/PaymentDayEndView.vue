<script setup>
import { onMounted, ref } from "vue";
import { paymentDayEndApi } from "../api/client";

const overview = ref({
  totalBatchCount: 0,
  completedBatchCount: 0,
  abnormalBatchCount: 0,
  latestBizDate: "",
  latestBatchStatus: "未跑批",
  openChannelAbnormalCount: 0,
  openInternalAbnormalCount: 0,
  openPendingRefundCount: 0,
  alerts: [],
  recentBatches: []
});
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeAction = ref("");
const runForm = ref({
  bizDate: "2026-07-19",
  triggeredBy: "finance-ops",
  runMode: "MANUAL"
});

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentDayEndApi.getOverview();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

async function runBatch() {
  activeAction.value = "run";
  actionMessage.value = "";
  try {
    overview.value = await paymentDayEndApi.runBatch(runForm.value);
    actionMessage.value = `已触发 ${runForm.value.bizDate} 的支付日终处理。`;
  } catch (error) {
    actionMessage.value = `支付日终处理触发失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

onMounted(loadOverview);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付日终处理</h2>
        <p>统一收口支付、退款、渠道回调和内部事件，作为后续对账与差错处理的前置事实</p>
      </div>
      <button class="button secondary" @click="loadOverview">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付日终数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付日终数据加载中...</div>

      <template v-else>
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">累计跑批次数</div>
            <div class="detail-value">{{ overview.totalBatchCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">完成批次数</div>
            <div class="detail-value">{{ overview.completedBatchCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">异常批次数</div>
            <div class="detail-value">{{ overview.abnormalBatchCount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">最近业务日</div>
            <div class="detail-value">{{ overview.latestBizDate || "暂无" }}</div>
          </div>
        </div>

        <div class="risk-banner">
          当前未收口：渠道异常 {{ overview.openChannelAbnormalCount }} 笔，内部事件异常 {{ overview.openInternalAbnormalCount }} 笔，待收口退款 {{ overview.openPendingRefundCount }} 笔。
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>对账准入判断</h3>
              <p class="meta">日终处理 V1.7 已给出统一准入口径，先判断是否能进入正式对账，再安排差错处理顺序</p>
            </div>
          </div>
          <div class="detail-card-grid">
            <div class="detail-card">
              <div class="detail-label">当前结论</div>
              <div class="detail-value">
                <span :class="['badge', overview.reconciliationReadinessType]">
                  {{ overview.reconciliationReadinessStatus || "未生成日终事实" }}
                </span>
              </div>
              <div class="detail-hint">{{ overview.reconciliationReadinessSummary }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">建议动作</div>
              <div class="detail-hint">{{ overview.reconciliationSuggestedAction || "先执行日终跑批并补齐事实快照" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">责任方</div>
              <div class="detail-value">{{ overview.reconciliationBlockingOwner || "支付研发 / 财务值班" }}</div>
              <div class="detail-hint">用于安排次日对账前的责任归口</div>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>最近批次对账前置事实</h3>
              <p class="meta">用于财务和运营先确认支付成功、渠道收口、内部事件收口是否一致，再决定是否进入正式对账。</p>
            </div>
          </div>
          <div class="detail-card-grid">
            <div class="detail-card">
              <div class="detail-label">渠道成功收口</div>
              <div class="detail-value">{{ overview.latestChannelSuccessCount ?? 0 }}</div>
              <div class="detail-hint">金额 {{ overview.latestChannelSuccessAmount || "¥0.00" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">内部事件成功</div>
              <div class="detail-value">{{ overview.latestInternalSuccessCount ?? 0 }}</div>
              <div class="detail-hint">金额 {{ overview.latestInternalSuccessAmount || "¥0.00" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">支付成功差异</div>
              <div class="detail-value">{{ overview.latestPaymentSuccessGapCount ?? 0 }}</div>
              <div class="detail-hint">金额 {{ overview.latestPaymentSuccessGapAmount || "¥0.00" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">待收口退款金额</div>
              <div class="detail-value">{{ overview.latestPendingRefundAmount || "¥0.00" }}</div>
              <div class="detail-hint">对应 {{ overview.openPendingRefundCount ?? 0 }} 笔退款</div>
            </div>
          </div>
        </div>

        <div class="detail-card-grid">
          <div v-for="alert in overview.alerts" :key="alert.alertType" class="detail-card">
            <div class="detail-label">{{ alert.alertTitle }}</div>
            <div class="detail-value">{{ alert.affectedCount }}</div>
            <div class="detail-hint">{{ alert.alertMessage }}</div>
            <div class="detail-hint">{{ alert.suggestedAction }}</div>
            <RouterLink class="link-button" :to="alert.actionRoute">查看</RouterLink>
          </div>
        </div>

        <div class="sub-panel">
          <h3>手动触发日终处理</h3>
          <p>适用于补跑、账期重检和渠道异常排查，当前 V1 会同步生成一条日终批次留痕。</p>

          <div class="toolbar compact-toolbar">
            <div class="field">
              <label>业务日期</label>
              <input v-model.trim="runForm.bizDate" placeholder="2026-07-19" />
            </div>
            <div class="field">
              <label>运行方式</label>
              <select v-model="runForm.runMode">
                <option value="MANUAL">MANUAL</option>
                <option value="AUTO">AUTO</option>
                <option value="RERUN">RERUN</option>
              </select>
            </div>
            <div class="field wide-field">
              <label>触发人</label>
              <input v-model.trim="runForm.triggeredBy" placeholder="finance-ops" />
            </div>
            <div class="toolbar-actions">
              <button class="button primary" :disabled="activeAction === 'run'" @click="runBatch">触发跑批</button>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>最近日终批次</h3>
              <p class="meta">重点关注渠道异常、内部事件异常和待收口退款，作为第二天对账和差错处理入口</p>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>批次号</th>
                  <th>业务日期</th>
                  <th>运行方式</th>
                  <th>批次状态</th>
                  <th>支付总单量</th>
                  <th>支付成功单量</th>
                  <th>支付成功金额</th>
                  <th>渠道成功单量</th>
                  <th>渠道成功金额</th>
                  <th>内部成功单量</th>
                  <th>内部成功金额</th>
                  <th>成功差异单量</th>
                  <th>成功差异金额</th>
                  <th>退款成功单量</th>
                  <th>退款成功金额</th>
                  <th>渠道异常</th>
                  <th>内部异常</th>
                  <th>待收口退款</th>
                  <th>待收口退款金额</th>
                  <th>对账准入</th>
                  <th>备注</th>
                  <th>触发人</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in overview.recentBatches" :key="item.batchNo">
                  <td>{{ item.batchNo }}</td>
                  <td>{{ item.bizDate }}</td>
                  <td>{{ item.runMode }}</td>
                  <td><span :class="['badge', item.batchStatusType]">{{ item.batchStatus }}</span></td>
                  <td>{{ item.paymentTotalCount }}</td>
                  <td>{{ item.paymentSuccessCount }}</td>
                  <td>{{ item.paymentSuccessAmount }}</td>
                  <td>{{ item.channelSuccessCount }}</td>
                  <td>{{ item.channelSuccessAmount }}</td>
                  <td>{{ item.internalSuccessCount }}</td>
                  <td>{{ item.internalSuccessAmount }}</td>
                  <td>{{ item.paymentSuccessGapCount }}</td>
                  <td>{{ item.paymentSuccessGapAmount }}</td>
                  <td>{{ item.refundSuccessCount }}</td>
                  <td>{{ item.refundSuccessAmount }}</td>
                  <td>{{ item.channelAbnormalCount }}</td>
                  <td>{{ item.internalAbnormalCount }}</td>
                  <td>{{ item.pendingRefundCount }}</td>
                  <td>{{ item.pendingRefundAmount }}</td>
                  <td class="flow-summary-cell">
                    <span :class="['badge', item.reconciliationReadinessType]">{{ item.reconciliationReadinessStatus }}</span>
                    <div class="detail-hint">{{ item.reconciliationReadinessSummary }}</div>
                  </td>
                  <td class="flow-summary-cell">{{ item.summaryComment }}</td>
                  <td>{{ item.triggeredBy }}</td>
                  <td>{{ item.completedAt }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
