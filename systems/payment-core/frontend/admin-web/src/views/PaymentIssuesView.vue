<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentIssueApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const items = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const selectedIssueNos = ref([]);
const actionForm = ref({
  actionType: "分派处理人",
  assignee: "支付运营",
  operator: "支付运营",
  remark: "请按建议动作完成排查并回写处理结果"
});
const filters = ref({
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  issueType: route.query.issueType || "全部",
  severity: route.query.severity || "全部",
  channelCode: route.query.channelCode || "",
  paymentMethod: route.query.paymentMethod || "全部"
});

const responsibilitySummaries = computed(() => {
  const summaryMap = new Map();
  items.value.forEach((item) => {
    const groupName = item.responsibilityGroup || "未识别责任组";
    const current = summaryMap.get(groupName) || {
      groupName,
      groupType: item.responsibilityGroupType || "info",
      count: 0,
      overdueCount: 0
    };
    current.count += 1;
    if (item.slaStatus === "已超时") {
      current.overdueCount += 1;
      current.groupType = "danger";
    }
    summaryMap.set(groupName, current);
  });
  return Array.from(summaryMap.values());
});

function resetFilters() {
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
  pageNo.value = 1;
  loadIssues();
}

async function loadIssues() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentIssueApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      issueType: filters.value.issueType,
      severity: filters.value.severity,
      channelCode: filters.value.channelCode,
      paymentMethod: filters.value.paymentMethod,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    selectedIssueNos.value = selectedIssueNos.value.filter((issueNo) =>
      result.items.some((item) => item.issueNo === issueNo)
    );
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
  loadIssues();
}

function openIssue(item) {
  if (item.recommendedRoute) {
    router.push(item.recommendedRoute);
    return;
  }
  router.push(`/payments/${item.paymentOrderId}`);
}

function toggleIssueSelection(issueNo, checked) {
  if (checked && !selectedIssueNos.value.includes(issueNo)) {
    selectedIssueNos.value = [...selectedIssueNos.value, issueNo];
    return;
  }
  if (!checked) {
    selectedIssueNos.value = selectedIssueNos.value.filter((selectedIssueNo) => selectedIssueNo !== issueNo);
  }
}

async function submitBatchAction() {
  if (!selectedIssueNos.value.length) {
    actionMessage.value = "请先选择需要处理的异常。";
    return;
  }
  actionMessage.value = "";
  try {
    const result = await paymentIssueApi.batchAction({
      issueNos: selectedIssueNos.value,
      actionType: actionForm.value.actionType,
      assignee: actionForm.value.assignee,
      operator: actionForm.value.operator,
      remark: actionForm.value.remark
    });
    items.value = result.items;
    total.value = result.total;
    selectedIssueNos.value = [];
    actionMessage.value = "批量处理动作已记录，异常中心列表已刷新。";
  } catch (error) {
    actionMessage.value = `批量处理失败：${error.message}`;
  }
}

onMounted(loadIssues);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付交易异常中心</h2>
        <p>统一聚合待回调、回调待处理、下游事件失败和停用渠道命中问题，支撑运营与研发排障</p>
      </div>
      <button class="button secondary" @click="loadIssues">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付交易异常加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="notice-banner">
        {{ actionMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>异常类型</label>
          <select v-model="filters.issueType">
            <option>全部</option>
            <option>待回调未收口</option>
            <option>回调处理待跟进</option>
            <option>下游事件发布失败</option>
            <option>命中停用渠道</option>
          </select>
        </div>
        <div class="field">
          <label>严重等级</label>
          <select v-model="filters.severity">
            <option>全部</option>
            <option>P1</option>
            <option>P2</option>
          </select>
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" placeholder="如 alipay_h5 / wx_jsapi" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select v-model="filters.paymentMethod">
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
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="toolbar">
        <div class="field">
          <label>批量动作</label>
          <select v-model="actionForm.actionType">
            <option>分派处理人</option>
            <option>标记跟进中</option>
            <option>标记已处理</option>
            <option>补充备注</option>
          </select>
        </div>
        <div class="field">
          <label>处理人</label>
          <input v-model="actionForm.assignee" placeholder="如 支付运营 / 后端值班" />
        </div>
        <div class="field">
          <label>操作人</label>
          <input v-model="actionForm.operator" placeholder="请输入操作人" />
        </div>
        <div class="field wide-field">
          <label>处理备注</label>
          <input v-model="actionForm.remark" placeholder="请写清楚处理结论、排查方向或交接说明" />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="submitBatchAction">批量处理选中异常</button>
        </div>
      </div>

      <div v-if="responsibilitySummaries.length" class="detail-card-grid">
        <div v-for="summary in responsibilitySummaries" :key="summary.groupName" class="detail-card">
          <div class="detail-label">责任组</div>
          <div class="detail-value">{{ summary.groupName }}</div>
          <div class="detail-hint">
            当前页 {{ summary.count }} 条，SLA 超时 {{ summary.overdueCount }} 条
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付交易异常加载中...</div>

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
                  @change="toggleIssueSelection(item.issueNo, $event.target.checked)"
                />
              </td>
              <td>{{ item.issueNo }}</td>
              <td>
                <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                  {{ item.paymentOrderId }}
                </RouterLink>
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
                <span :class="['badge', item.slaStatusType]">{{ item.slaStatus }}</span>
                <div class="muted-text">{{ item.slaTimeLeft }}</div>
              </td>
              <td><span :class="['badge', item.escalationStatusType]">{{ item.escalationStatus }}</span></td>
              <td class="flow-summary-cell">{{ item.escalationSuggestion }}</td>
              <td class="flow-summary-cell">{{ item.latestActionSummary }}</td>
              <td>{{ item.createdAt }}</td>
              <td>
                <button class="link-button" @click="openIssue(item)">立即排查</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span>共 {{ total }} 条支付交易异常</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>
