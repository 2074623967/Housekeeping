<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentDeadLetterTaskApi } from "../api/client";
import { replayActionMessage } from "../utils/deadLetterAction";

const router = useRouter();
const items = ref([]);
const selectedItem = ref(null);
const total = ref(0);
const pageNo = ref(1);
const loadedPageNo = ref(1);
const loadedQuery = ref(null);
const pageSize = 20;
const isLoading = ref(true);
const hasLoadedData = ref(false);
const activeAction = ref("");
const loadErrorMessage = ref("");
const actionErrorMessage = ref("");
const successMessage = ref("");
const operator = ref("");
const resolutionNote = ref("");
const filters = ref({
  taskStatus: "",
  targetSystem: "",
  messageId: ""
});
let latestLoadRequestId = 0;

const isSnapshotStale = computed(() => hasLoadedData.value && Boolean(loadErrorMessage.value));
const isInteractionLocked = computed(() => isLoading.value || Boolean(activeAction.value));

const metricCards = computed(() => [
  { title: "任务总数", value: total.value, hint: "当前筛选条件下的死信补偿任务" },
  {
    title: "待复核",
    value: items.value.filter((item) => item.taskStatus === "PENDING_REVIEW").length,
    hint: "当前页尚未完成人工判断的任务"
  },
  {
    title: "待重放",
    value: items.value.filter((item) => item.taskStatus === "READY_TO_REPLAY").length,
    hint: "当前页已复核、等待定向重放的任务"
  },
  {
    title: "重放失败",
    value: items.value.filter((item) => item.taskStatus === "REPLAY_FAILED").length,
    hint: "当前页需要重新核查消息代理或消费者的任务"
  }
]);

const selectedSuggestion = computed(() => {
  const status = selectedItem.value?.taskStatus;
  if (status === "PENDING_REVIEW") {
    return "先核对原消息事实、目标消费者和幂等能力，再决定允许重放或人工结案。";
  }
  if (status === "READY_TO_REPLAY") {
    return "当前任务已通过人工复核，可以执行一次定向重放；完成后仍需核对消费者处理结果。";
  }
  if (status === "REPLAY_FAILED") {
    return "最近重放未获得消息代理确认，请排查代理连接与路由后重新复核，不能据此推断下游未处理。";
  }
  if (status === "REPLAYING") {
    return "任务正在重放，等待服务端完成发布确认，避免重复操作。";
  }
  if (status === "REPLAYED") {
    return "服务端已完成重放发布确认，仍需在目标系统核对幂等消费和业务收口。";
  }
  return "任务已人工结案，不再重放；处置说明应保留可追溯的判断依据。";
});

function resetFilters() {
  if (isInteractionLocked.value) {
    return;
  }
  filters.value = { taskStatus: "", targetSystem: "", messageId: "" };
  pageNo.value = 1;
  loadTasks();
}

function applyFilters() {
  if (isInteractionLocked.value) {
    return;
  }
  pageNo.value = 1;
  loadTasks();
}

function pickItem(item) {
  selectedItem.value = item;
}

function openDetail(item) {
  router.push(`/payment-dead-letter-tasks/${encodeURIComponent(item.taskNo)}`);
}

function buildQuery() {
  return {
    taskStatus: filters.value.taskStatus,
    targetSystem: filters.value.targetSystem,
    messageId: filters.value.messageId.trim(),
    pageNo: pageNo.value,
    pageSize
  };
}

async function loadTasks(preferredTaskNo = selectedItem.value?.taskNo, query = buildQuery()) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  actionErrorMessage.value = "";
  try {
    const result = await paymentDeadLetterTaskApi.getList(query);
    if (requestId !== latestLoadRequestId) {
      return false;
    }
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items.find((item) => item.taskNo === preferredTaskNo)
      || result.items[0]
      || null;
    pageNo.value = query.pageNo;
    loadedPageNo.value = query.pageNo;
    loadedQuery.value = { ...query };
    hasLoadedData.value = true;
    return true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) {
      return false;
    }
    loadErrorMessage.value = error.message;
    if (hasLoadedData.value) {
      pageNo.value = loadedPageNo.value;
    }
    return false;
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

async function runAction(item, action) {
  const currentItem = items.value.find((candidate) => candidate.taskNo === item.taskNo);
  const isAllowedAction = action === "replay"
    ? currentItem?.taskStatus === "READY_TO_REPLAY"
    : ["ready", "resolve"].includes(action) && currentItem && canReview(currentItem);
  if (isInteractionLocked.value || isSnapshotStale.value || !hasLoadedData.value
      || !loadedQuery.value || !isAllowedAction) {
    actionErrorMessage.value = "当前任务快照不可操作，请刷新成功后重新确认任务状态";
    return;
  }
  const normalizedOperator = operator.value.trim();
  const normalizedNote = resolutionNote.value.trim();
  if (!normalizedOperator) {
    actionErrorMessage.value = "请先输入处置人";
    return;
  }
  if (!normalizedNote) {
    actionErrorMessage.value = "请先输入处置说明";
    return;
  }
  activeAction.value = `${item.taskNo}:${action}`;
  actionErrorMessage.value = "";
  successMessage.value = "";
  const payload = { operator: normalizedOperator, resolutionNote: normalizedNote };
  try {
    let actionResult;
    let replayMessage;
    if (action === "ready") {
      actionResult = await paymentDeadLetterTaskApi.markReadyToReplay(item.taskNo, payload);
    } else if (action === "resolve") {
      actionResult = await paymentDeadLetterTaskApi.resolveManually(item.taskNo, payload);
    } else {
      actionResult = await paymentDeadLetterTaskApi.replay(item.taskNo, payload);
      replayMessage = replayActionMessage(actionResult);
    }
    if (action === "ready") {
      successMessage.value = `任务 ${item.taskNo} 已通过复核，等待定向重放`;
    } else if (action === "resolve") {
      successMessage.value = `任务 ${item.taskNo} 已人工结案`;
    }
    const refreshed = await loadTasks(item.taskNo, { ...loadedQuery.value });
    if (!refreshed) {
      actionErrorMessage.value = `任务 ${item.taskNo} 操作已完成，但最新列表刷新失败，请刷新成功后再继续操作`;
    } else if (action === "replay") {
      if (replayMessage.tone === "error") {
        actionErrorMessage.value = replayMessage.message;
      } else {
        successMessage.value = replayMessage.message;
      }
    }
  } catch (error) {
    actionErrorMessage.value = `死信任务操作失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

function goToPage(nextPage) {
  if (isInteractionLocked.value || isSnapshotStale.value || !loadedQuery.value
      || nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadTasks(selectedItem.value?.taskNo, { ...loadedQuery.value, pageNo: nextPage });
}

function canReview(item) {
  return ["PENDING_REVIEW", "REPLAY_FAILED"].includes(item.taskStatus);
}

function statusLabel(status) {
  return {
    PENDING_REVIEW: "待复核",
    READY_TO_REPLAY: "待重放",
    REPLAYING: "重放中",
    REPLAYED: "已重放",
    REPLAY_FAILED: "重放失败",
    MANUAL_RESOLVED: "人工结案"
  }[status] || status;
}

onMounted(loadTasks);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>死信补偿任务</h2>
        <p>复核支付消息死信，按原消费者执行受控定向重放或人工结案</p>
      </div>
      <button class="button secondary" :disabled="isInteractionLocked" @click="loadTasks()">
        {{ isLoading ? "刷新中..." : "刷新" }}
      </button>
    </div>

    <section v-if="hasLoadedData" class="detail-card-grid">
      <article v-for="card in metricCards" :key="card.title" class="detail-card">
        <div class="detail-label">{{ card.title }}</div>
        <div class="detail-value">{{ card.value }}</div>
        <div class="meta">{{ card.hint }}</div>
      </article>
    </section>

    <section class="panel">
      <div class="notice-banner">
        重放只表示 payment-core 已向原消费者路由重新发布消息，不代表目标系统已消费、入账或完成业务处理。
      </div>
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "死信补偿任务刷新失败，当前展示上次成功快照：" : "死信补偿任务加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="actionErrorMessage" class="error-banner">{{ actionErrorMessage }}</div>
      <div v-if="successMessage" class="success-banner">{{ successMessage }}</div>

      <div class="filter-grid action-grid">
        <label>
          处置人
          <input v-model="operator" :disabled="isInteractionLocked" maxlength="64" placeholder="请输入处置人" />
        </label>
        <label class="wide-field">
          处置说明
          <input v-model="resolutionNote" :disabled="isInteractionLocked" maxlength="512" placeholder="请填写核对依据或结案原因" />
        </label>
        <label>
          任务状态
          <select v-model="filters.taskStatus" :disabled="isInteractionLocked">
            <option value="">全部</option>
            <option value="PENDING_REVIEW">待复核</option>
            <option value="READY_TO_REPLAY">待重放</option>
            <option value="REPLAYING">重放中</option>
            <option value="REPLAYED">已重放</option>
            <option value="REPLAY_FAILED">重放失败</option>
            <option value="MANUAL_RESOLVED">人工结案</option>
          </select>
        </label>
        <label>
          目标系统
          <select v-model="filters.targetSystem" :disabled="isInteractionLocked">
            <option value="">全部</option>
            <option value="clearing-system">清分系统</option>
            <option value="accounting-system">账务系统</option>
            <option value="settlement-system">结算系统</option>
          </select>
        </label>
        <label>
          消息 ID
          <input v-model="filters.messageId" :disabled="isInteractionLocked" maxlength="128" placeholder="请输入消息 ID" />
        </label>
        <div class="toolbar-actions">
          <button class="button primary" :disabled="isInteractionLocked" @click="applyFilters">查询</button>
          <button class="button secondary" :disabled="isInteractionLocked" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">死信补偿任务加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的死信补偿任务快照</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的死信补偿任务</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>任务号</th>
                <th>消息 ID</th>
                <th>目标系统</th>
                <th>任务状态</th>
                <th>重放次数</th>
                <th>最后处置人</th>
                <th>最近重放</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.taskNo">
                <td><button class="table-link" @click="pickItem(item)">{{ item.taskNo }}</button></td>
                <td>{{ item.messageId }}</td>
                <td>{{ item.targetSystem }}</td>
                <td><span :class="['badge', item.taskStatusType]">{{ statusLabel(item.taskStatus) }}</span></td>
                <td>{{ item.replayCount }}</td>
                <td>{{ item.operator || "-" }}</td>
                <td>{{ item.lastReplayAt || "-" }}</td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <div class="list-actions">
                    <button
                      class="link-button"
                      @click="openDetail(item)"
                    >详情</button>
                    <button
                      v-if="canReview(item)"
                      class="link-button"
                      :disabled="isInteractionLocked || isSnapshotStale || !hasLoadedData"
                      @click="runAction(item, 'ready')"
                    >允许重放</button>
                    <button
                      v-if="canReview(item)"
                      class="link-button"
                      :disabled="isInteractionLocked || isSnapshotStale || !hasLoadedData"
                      @click="runAction(item, 'resolve')"
                    >人工结案</button>
                    <button
                      v-if="item.taskStatus === 'READY_TO_REPLAY'"
                      class="link-button"
                      :disabled="isInteractionLocked || isSnapshotStale || !hasLoadedData"
                      @click="runAction(item, 'replay')"
                    >{{ activeAction === `${item.taskNo}:replay` ? "重放中..." : "执行重放" }}</button>
                    <span v-if="!canReview(item) && item.taskStatus !== 'READY_TO_REPLAY'" class="muted-text">无需操作</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>任务快照</h3>
              <span class="meta">{{ selectedItem.taskNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>关联 ID</span><strong>{{ selectedItem.correlationId || "-" }}</strong></div>
              <div class="detail-card"><span>当前状态</span><strong>{{ statusLabel(selectedItem.taskStatus) }}</strong></div>
              <div class="detail-card"><span>死信路由</span><strong>{{ selectedItem.deadLetterRoutingKey }}</strong></div>
              <div class="detail-card"><span>重放路由</span><strong>{{ selectedItem.replayRoutingKey }}</strong></div>
              <div class="detail-card"><span>最近处置</span><strong>{{ selectedItem.resolvedAt || "-" }}</strong></div>
              <div class="detail-card"><span>最后更新</span><strong>{{ selectedItem.updatedAt || "-" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">处置说明</div>
              <div class="timeline-item">{{ selectedItem.resolutionNote || "尚未记录人工处置说明" }}</div>
            </div>
            <div class="ops-card">
              <div class="ops-title">处理建议</div>
              <div class="timeline-item">{{ selectedSuggestion }}</div>
            </div>
            <div class="payload-grid snapshot-grid">
              <div>
                <strong>消息体脱敏快照</strong>
                <pre>{{ selectedItem.payloadSnapshot || "-" }}</pre>
              </div>
              <div>
                <strong>消息头脱敏快照</strong>
                <pre>{{ selectedItem.headerSnapshot || "-" }}</pre>
              </div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧任务后，可查看脱敏消息快照、路由和处置建议。</div>
        </aside>
      </div>

      <div v-if="hasLoadedData" class="pager">
        <span>共 {{ total }} 条死信补偿任务</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="isInteractionLocked || pageNo === 1 || isSnapshotStale" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="isInteractionLocked || pageNo >= Math.ceil(total / pageSize) || isSnapshotStale" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.action-grid {
  margin-bottom: 18px;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(340px, 0.8fr);
  gap: 16px;
}

.detail-side,
.detail-stack {
  min-width: 0;
}

.detail-stack {
  display: grid;
  gap: 16px;
}

.table-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

.snapshot-grid {
  grid-template-columns: 1fr;
  padding: 0;
}

.snapshot-grid pre {
  max-height: 220px;
}

@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
