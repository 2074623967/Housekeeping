<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentIssueApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import { paymentIssueAlertsRoute } from "../router/listRoutes";
import { paymentIssueListFiltersFromQuery } from "../utils/paymentIssueListFilters";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const isLoading = ref(true);
const isSummaryLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
const actionRefreshErrorMessage = ref("");
const actionErrorMessage = ref("");
const actionResultMessage = ref("");
const isActionRunning = ref(false);
const selectedIssueNos = ref([]);
const responsibilitySummaries = ref([]);
const loadedPageNo = ref(1);
const loadedQuery = ref(null);
const actionForm = ref({
  actionType: "分派处理人",
  assignee: "支付运营",
  operator: "",
  remark: "请按建议动作完成排查并回写处理结果"
});
const filters = ref(paymentIssueListFiltersFromQuery(route.query));
let latestLoadRequestId = 0;

const issueOverview = computed(() => ({
  groupCount: responsibilitySummaries.value.length,
  totalCount: responsibilitySummaries.value.reduce((sum, item) => sum + (item.totalCount || 0), 0),
  overdueCount: responsibilitySummaries.value.reduce((sum, item) => sum + (item.overdueCount || 0), 0),
  p1Count: responsibilitySummaries.value.reduce((sum, item) => sum + (item.p1Count || 0), 0),
  p2Count: responsibilitySummaries.value.reduce((sum, item) => sum + (item.p2Count || 0), 0)
}));

const isSnapshotStale = computed(() => Boolean(
  hasLoadedData.value && (loadErrorMessage.value || actionRefreshErrorMessage.value)
));
const isWriteDisabled = computed(() => (
  !hasLoadedData.value || isLoading.value || isSnapshotStale.value || isActionRunning.value
));
const isInteractionLocked = computed(() => isLoading.value || isActionRunning.value);
const canUseSnapshot = computed(() => hasLoadedData.value
  && !isInteractionLocked.value
  && !isSnapshotStale.value);

function buildQuery() {
  return {
    paymentOrderId: filters.value.paymentOrderId,
    orderNo: filters.value.orderNo,
    issueType: filters.value.issueType,
    severity: filters.value.severity,
    channelCode: filters.value.channelCode,
    paymentMethod: filters.value.paymentMethod,
    pageNo: pageNo.value,
    pageSize
  };
}

function buildSummaryQuery(query) {
  return {
    paymentOrderId: query.paymentOrderId,
    orderNo: query.orderNo,
    issueType: query.issueType,
    severity: query.severity,
    channelCode: query.channelCode,
    paymentMethod: query.paymentMethod
  };
}

function resetFilters() {
  if (isInteractionLocked.value) {
    return;
  }
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    issueType: "全部",
    severity: "全部",
    channelCode: "",
    paymentMethod: "全部"
  };
  pageNo.value = 1;
  loadIssues();
}

function applyFilters() {
  if (isInteractionLocked.value) {
    return;
  }
  pageNo.value = 1;
  loadIssues();
}

async function loadIssues(afterAction = false, query = buildQuery()) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  isSummaryLoading.value = true;
  loadErrorMessage.value = "";
  actionRefreshErrorMessage.value = "";
  try {
    const summaryPromise = paymentIssueApi.getResponsibilitySummary(buildSummaryQuery(query));
    const listPromise = paymentIssueApi.getList(query);
    const [summaryResult, result] = await Promise.all([summaryPromise, listPromise]);
    if (requestId !== latestLoadRequestId) {
      return false;
    }
    responsibilitySummaries.value = summaryResult;
    items.value = result.items;
    total.value = result.total;
    selectedIssueNos.value = selectedIssueNos.value.filter((issueNo) =>
      result.items.some((item) => item.issueNo === issueNo)
    );
    pageNo.value = query.pageNo;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
    if (!afterAction) {
      actionErrorMessage.value = "";
      actionResultMessage.value = "";
    }
    return true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return false;
    }
    if (afterAction) {
      actionRefreshErrorMessage.value = error.message;
    } else {
      loadErrorMessage.value = error.message;
      actionRefreshErrorMessage.value = "";
    }
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
    return false;
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
      isSummaryLoading.value = false;
    }
  }
}

function goToPage(nextPage) {
  if (!canUseSnapshot.value || !loadedQuery.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadIssues(false, { ...loadedQuery.value, pageNo: nextPage });
}

function openIssue(item) {
  const currentItem = items.value.find((candidate) => candidate.issueNo === item.issueNo);
  if (!canUseSnapshot.value || !currentItem) {
    return;
  }
  if (currentItem.recommendedRoute) {
    router.push(currentItem.recommendedRoute);
    return;
  }
  router.push(paymentDetailRoute(currentItem.paymentOrderId));
}

function openSnapshotRoute(target) {
  if (!canUseSnapshot.value || !target) {
    return;
  }
  router.push(target);
}

function toggleIssueSelection(issueNo, checked) {
  if (isWriteDisabled.value || !items.value.some((item) => item.issueNo === issueNo)) {
    return;
  }
  if (checked && !selectedIssueNos.value.includes(issueNo)) {
    selectedIssueNos.value = [...selectedIssueNos.value, issueNo];
    return;
  }
  if (!checked) {
    selectedIssueNos.value = selectedIssueNos.value.filter((selectedIssueNo) => selectedIssueNo !== issueNo);
  }
}

async function submitBatchAction() {
  const hasInvalidSelection = selectedIssueNos.value.some((issueNo) =>
    !items.value.some((item) => item.issueNo === issueNo)
  );
  if (isWriteDisabled.value || !loadedQuery.value || hasInvalidSelection) {
    actionErrorMessage.value = "当前异常快照不可操作，请刷新成功后重新选择异常。";
    actionResultMessage.value = "";
    return;
  }
  if (!selectedIssueNos.value.length) {
    actionErrorMessage.value = "请先选择需要处理的异常。";
    actionResultMessage.value = "";
    return;
  }
  const operator = actionForm.value.operator.trim();
  if (!operator) {
    actionErrorMessage.value = "请先输入操作人。";
    actionResultMessage.value = "";
    return;
  }
  const issueNos = [...selectedIssueNos.value];
  const query = { ...loadedQuery.value };
  isActionRunning.value = true;
  actionErrorMessage.value = "";
  actionResultMessage.value = "";
  try {
    await paymentIssueApi.batchAction({
      issueNos,
      actionType: actionForm.value.actionType,
      assignee: actionForm.value.assignee,
      operator,
      remark: actionForm.value.remark
    });
    selectedIssueNos.value = [];
    actionResultMessage.value = `已记录 ${issueNos.length} 条异常的批量处理动作。`;
    actionRefreshErrorMessage.value = "";
    await loadIssues(true, query);
  } catch (error) {
    actionErrorMessage.value = `批量处理失败：${error.message}`;
  } finally {
    isActionRunning.value = false;
  }
}

onMounted(loadIssues);

watch(
  () => [
    route.query.paymentOrderId,
    route.query.orderNo,
    route.query.issueType,
    route.query.severity,
    route.query.channelCode,
    route.query.paymentMethod
  ],
  () => {
    filters.value = paymentIssueListFiltersFromQuery(route.query);
    pageNo.value = 1;
    selectedIssueNos.value = [];
    loadIssues();
  }
);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付交易异常中心</h2>
        <p>统一聚合支付状态、下游事件、渠道配置和超额成功收款问题，支撑运营、财务与研发排障</p>
      </div>
      <button class="button secondary" :disabled="isInteractionLocked" @click="loadIssues()">刷新</button>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付交易异常刷新失败，当前展示上次成功快照：" : "支付交易异常加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionRefreshErrorMessage" class="error-banner">
        批量处理结果已确认，但异常列表与责任组统计刷新失败，当前展示上次成功快照：{{ actionRefreshErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">
        {{ actionErrorMessage }}
      </div>
      <div v-if="actionResultMessage" class="success-banner">
        {{ actionResultMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" :disabled="isInteractionLocked" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" :disabled="isInteractionLocked" />
        </div>
        <div class="field">
          <label>异常类型</label>
          <select v-model="filters.issueType" :disabled="isInteractionLocked">
            <option>全部</option>
            <option>待回调未收口</option>
            <option>回调处理待跟进</option>
            <option>下游事件发布失败</option>
            <option>退款提交结果待核查</option>
            <option>命中停用渠道</option>
            <option>订单成功收款超额</option>
          </select>
        </div>
        <div class="field">
          <label>严重等级</label>
          <select v-model="filters.severity" :disabled="isInteractionLocked">
            <option>全部</option>
            <option>P1</option>
            <option>P2</option>
          </select>
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" placeholder="如 alipay_h5 / wx_jsapi" :disabled="isInteractionLocked" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select v-model="filters.paymentMethod" :disabled="isInteractionLocked">
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行转账</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="该页用于支付主链路异常聚合，便于统一排障与联查" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isInteractionLocked" @click="applyFilters">查询</button>
          <button class="button secondary" :disabled="isInteractionLocked" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="toolbar">
        <div class="field">
          <label>批量动作</label>
          <select v-model="actionForm.actionType" :disabled="isWriteDisabled">
            <option>分派处理人</option>
            <option>标记跟进中</option>
            <option>标记已处理</option>
            <option>补充备注</option>
          </select>
        </div>
        <div class="field">
          <label>处理人</label>
          <input v-model="actionForm.assignee" maxlength="64" placeholder="如 支付运营 / 后端值班" :disabled="isWriteDisabled" />
        </div>
        <div class="field">
          <label>操作人</label>
          <input v-model="actionForm.operator" maxlength="64" placeholder="请输入操作人" :disabled="isWriteDisabled" />
        </div>
        <div class="field wide-field">
          <label>处理备注</label>
          <input v-model="actionForm.remark" maxlength="255" placeholder="请写清楚处理结论、排查方向或交接说明" :disabled="isWriteDisabled" />
        </div>
        <div class="toolbar-actions">
          <button
            class="button primary"
            :disabled="isWriteDisabled"
            @click="submitBatchAction"
          >
            批量处理选中异常
          </button>
        </div>
      </div>

      <div v-if="isSummaryLoading" class="state-box">责任组统计加载中...</div>
      <section v-else-if="responsibilitySummaries.length" class="panel overview-panel">
        <div class="section-title">
          <h3>异常风险总览</h3>
          <span class="meta">按责任组、严重等级和 SLA 超时统一观察风险面</span>
        </div>
        <div class="overview-grid">
          <article class="overview-card danger">
            <p class="overview-title">SLA 超时异常</p>
            <strong>{{ issueOverview.overdueCount }}</strong>
            <span>优先升级已超时未收口异常，避免影响支付回调、下游事件和日终准入。</span>
          </article>
          <article class="overview-card warn">
            <p class="overview-title">P1 高优先级异常</p>
            <strong>{{ issueOverview.p1Count }}</strong>
            <span>建议优先联查支付单、任务中心和异常告警明细，确认是否已触发升级和值班派发。</span>
          </article>
          <article class="overview-card info">
            <p class="overview-title">责任组覆盖</p>
            <strong>{{ issueOverview.groupCount }} / {{ issueOverview.totalCount }}</strong>
            <span>展示当前筛选范围涉及的责任组数量与异常总量，便于判断归口是否集中或扩散。</span>
          </article>
        </div>
      </section>
      <div v-else class="state-box">当前筛选范围暂无责任组统计</div>
      <div v-if="!isSummaryLoading && hasLoadedData && responsibilitySummaries.length" class="detail-card-grid">
        <div v-for="summary in responsibilitySummaries" :key="summary.groupName" class="detail-card">
          <div class="detail-label">责任组</div>
          <div class="detail-value">{{ summary.groupName }}</div>
          <div class="detail-hint">
            当前筛选 {{ summary.totalCount }} 条，SLA 超时 {{ summary.overdueCount }} 条
          </div>
          <div class="detail-hint">
            P1 {{ summary.p1Count }} 条，P2 {{ summary.p2Count }} 条
          </div>
          <div class="detail-hint">
            {{ summary.suggestedAction }}
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付交易异常加载中...</div>

      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付交易异常快照</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付交易异常</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>选择</th>
              <th>异常编号</th>
              <th>支付单号</th>
              <th>订单号</th>
              <th>客户</th>
              <th>支付方式</th>
              <th>渠道编码</th>
              <th>异常类型</th>
              <th>严重等级</th>
              <th>支付状态</th>
              <th>异常摘要</th>
              <th>根因提示</th>
              <th>建议动作</th>
              <th>处理状态</th>
              <th>当前处理人</th>
              <th>责任组</th>
              <th>告警通知</th>
              <th>SLA 状态</th>
              <th>升级状态</th>
              <th>升级建议</th>
              <th>最近动作</th>
              <th>异常时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.issueNo">
              <td>
                <input
                  type="checkbox"
                  :checked="selectedIssueNos.includes(item.issueNo)"
                  :disabled="isWriteDisabled"
                  @change="toggleIssueSelection(item.issueNo, $event.target.checked)"
                />
              </td>
              <td>{{ item.issueNo }}</td>
              <td>
                <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(paymentDetailRoute(item.paymentOrderId))">
                  {{ item.paymentOrderId }}
                </button>
              </td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.paymentMethod }}</td>
              <td>{{ item.channelCode }}</td>
              <td><span :class="['badge', item.issueTypeTag]">{{ item.issueType }}</span></td>
              <td><span :class="['badge', item.severityType]">{{ item.severity }}</span></td>
              <td><span :class="['badge', item.paymentStatusType]">{{ item.paymentStatus }}</span></td>
              <td class="flow-summary-cell">{{ item.issueSummary }}</td>
              <td class="flow-summary-cell">{{ item.rootCauseHint }}</td>
              <td class="flow-summary-cell">{{ item.recommendedAction }}</td>
              <td><span :class="['badge', item.handlingStatusType]">{{ item.handlingStatus }}</span></td>
              <td>{{ item.assignee }}</td>
              <td>
                <span :class="['badge', item.responsibilityGroupType]">{{ item.responsibilityGroup }}</span>
                <div class="muted-text">{{ item.responsibilityHint }}</div>
              </td>
              <td>
                <span :class="['badge', item.alertStatusType]">{{ item.alertStatus }}</span>
                <div class="muted-text">
                  {{ item.alertReceiver }} / {{ item.alertAckStatus }}
                </div>
                <div class="muted-text">
                  {{ item.alertChannelSummary }}
                </div>
                <div class="muted-text">
                  {{ item.alertDeliverySummary }}
                </div>
                <div class="muted-text">
                  {{ item.latestAlertProviderSummary }}
                </div>
                <div class="muted-text">
                  {{ item.latestAlertReceiptSummary }}
                </div>
                <button class="link-button" :disabled="!canUseSnapshot" @click="openSnapshotRoute(paymentIssueAlertsRoute({ issueNo: item.issueNo }))">
                  查看明细
                </button>
              </td>
              <td>
                <span :class="['badge', item.slaStatusType]">{{ item.slaStatus }}</span>
                <div class="muted-text">{{ item.slaTimeLeft }}</div>
              </td>
              <td><span :class="['badge', item.escalationStatusType]">{{ item.escalationStatus }}</span></td>
              <td class="flow-summary-cell">{{ item.escalationSuggestion }}</td>
              <td class="flow-summary-cell">{{ item.latestActionSummary }}</td>
              <td>{{ item.createdAt }}</td>
              <td>
                <button class="link-button" :disabled="!canUseSnapshot" @click="openIssue(item)">立即排查</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="hasLoadedData" class="pager">
        <span>共 {{ total }} 条支付交易异常</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="!canUseSnapshot || pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="!canUseSnapshot || pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>
