<script setup>
import { computed, onMounted, ref } from "vue";
import { paymentEventApi } from "../api/client";

const items = ref([]);
const selectedItem = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const activeEventNo = ref("");
const filters = ref({
  paymentOrderId: "",
  eventType: "全部",
  publishStatus: "全部",
  downstreamSystem: "全部",
  eventTopic: "",
  sortField: "createdAt",
  sortOrder: "desc"
});

const metrics = computed(() => ({
  total: total.value,
  successTotal: items.value.filter((item) => item.publishStatus === "SUCCESS").length,
  failedTotal: items.value.filter((item) => item.publishStatus === "FAILED").length,
  downstreamCount: new Set(items.value.map((item) => item.downstreamSystem).filter(Boolean)).size
}));

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    eventType: "全部",
    publishStatus: "全部",
    downstreamSystem: "全部",
    eventTopic: "",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  pageNo.value = 1;
  loadEvents();
}

function applyFilters() {
  pageNo.value = 1;
  loadEvents();
}

function pickItem(item) {
  selectedItem.value = item;
}

async function loadEvents() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentEventApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      eventType: filters.value.eventType,
      publishStatus: filters.value.publishStatus,
      downstreamSystem: filters.value.downstreamSystem,
      eventTopic: filters.value.eventTopic,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    selectedItem.value = result.items[0] || null;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function exportEvents() {
  const exportUrl = paymentEventApi.buildExportUrl({
    paymentOrderId: filters.value.paymentOrderId,
    eventType: filters.value.eventType,
    publishStatus: filters.value.publishStatus,
    downstreamSystem: filters.value.downstreamSystem,
    eventTopic: filters.value.eventTopic,
    sortField: filters.value.sortField,
    sortOrder: filters.value.sortOrder
  });
  window.open(exportUrl, "_blank", "noopener,noreferrer");
}

async function republish(item) {
  activeEventNo.value = item.eventNo;
  actionMessage.value = "";
  try {
    const result = await paymentEventApi.republish(item.eventNo, {
      paymentOrderId: filters.value.paymentOrderId,
      eventType: filters.value.eventType,
      publishStatus: filters.value.publishStatus,
      downstreamSystem: filters.value.downstreamSystem,
      eventTopic: filters.value.eventTopic,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
    actionMessage.value = `事件 ${item.eventNo} 已重新投递。`;
  } catch (error) {
    actionMessage.value = `事件 ${item.eventNo} 重发失败：${error.message}`;
  } finally {
    activeEventNo.value = "";
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadEvents();
}

onMounted(loadEvents);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付事件出站</h2>
        <p>统一管理 payment-core 向账户、清分、结算等下游系统投递的事件边界与重发动作</p>
      </div>
      <button class="button primary" @click="exportEvents">导出事件</button>
    </div>

    <section class="card-grid">
      <article class="card">
        <p class="card-title">事件总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">发布成功</p>
        <p class="card-value">{{ metrics.successTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">发布失败</p>
        <p class="card-value">{{ metrics.failedTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">下游系统数</p>
        <p class="card-value">{{ metrics.downstreamCount }}</p>
      </article>
    </section>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付事件出站数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>事件类型</label>
          <select v-model="filters.eventType">
            <option>全部</option>
            <option>PAYMENT_SUBMIT</option>
            <option>PAYMENT_PENDING</option>
            <option>PAYMENT_SUCCESS</option>
            <option>PAYMENT_CLOSED</option>
            <option>PAYMENT_EXPIRED_CLOSED</option>
          </select>
        </div>
        <div class="field">
          <label>发布状态</label>
          <select v-model="filters.publishStatus">
            <option>全部</option>
            <option>PENDING</option>
            <option>SUCCESS</option>
            <option>FAILED</option>
          </select>
        </div>
        <div class="field">
          <label>下游系统</label>
          <select v-model="filters.downstreamSystem">
            <option>全部</option>
            <option>gateway-access</option>
            <option>accounting-system</option>
            <option>clearing-system</option>
            <option>settlement-system</option>
            <option>payment-core-ops</option>
          </select>
        </div>
        <div class="field">
          <label>事件主题</label>
          <input v-model="filters.eventTopic" placeholder="如 payment.trade.succeeded.v1" />
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="retryCount">重试次数</option>
            <option value="nextRetryAt">下次重试时间</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付事件出站数据加载中...</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付事件出站数据</div>

      <div v-else class="detail-layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>事件号</th>
                <th>事件类型</th>
                <th>支付单号</th>
                <th>业务单号</th>
                <th>事件主题</th>
                <th>下游系统</th>
                <th>发布状态</th>
                <th>重试次数</th>
                <th>最近发布时间</th>
                <th>下次重试时间</th>
                <th>事件摘要</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.eventNo">
                <td>{{ item.eventNo }}</td>
                <td>{{ item.eventType }}</td>
                <td>{{ item.paymentOrderId }}</td>
                <td>{{ item.bizNo }}</td>
                <td>{{ item.eventTopic }}</td>
                <td>{{ item.downstreamSystem }}</td>
                <td><span :class="['badge', item.publishStatusType]">{{ item.publishStatus }}</span></td>
                <td>{{ item.retryCount }}</td>
                <td>{{ item.lastPublishedAt || "-" }}</td>
                <td>{{ item.nextRetryAt || "-" }}</td>
                <td class="flow-summary-cell">{{ item.eventPayload }}</td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <button class="link-button" @click="pickItem(item)">快照</button>
                  <button
                    class="link-button"
                    :disabled="activeEventNo === item.eventNo || item.publishStatus === 'SUCCESS'"
                    @click="republish(item)"
                  >
                    {{ item.publishStatus === "SUCCESS" ? "已发布" : "重发" }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="detail-side">
          <div v-if="selectedItem" class="detail-stack">
            <div class="section-title">
              <h3>事件快照</h3>
              <span class="meta">{{ selectedItem.eventNo }}</span>
            </div>
            <div class="detail-grid">
              <div class="detail-card"><span>事件类型</span><strong>{{ selectedItem.eventType }}</strong></div>
              <div class="detail-card"><span>支付单号</span><strong>{{ selectedItem.paymentOrderId }}</strong></div>
              <div class="detail-card"><span>业务单号</span><strong>{{ selectedItem.bizNo }}</strong></div>
              <div class="detail-card"><span>事件主题</span><strong>{{ selectedItem.eventTopic }}</strong></div>
              <div class="detail-card"><span>下游系统</span><strong>{{ selectedItem.downstreamSystem }}</strong></div>
              <div class="detail-card"><span>发布状态</span><strong>{{ selectedItem.publishStatus }}</strong></div>
              <div class="detail-card"><span>重试次数</span><strong>{{ selectedItem.retryCount }}</strong></div>
              <div class="detail-card"><span>最近发布时间</span><strong>{{ selectedItem.lastPublishedAt || "—" }}</strong></div>
              <div class="detail-card detail-card-wide"><span>下次重试时间</span><strong>{{ selectedItem.nextRetryAt || "—" }}</strong></div>
            </div>
            <div class="ops-card">
              <div class="ops-title">排障建议</div>
              <div class="ops-row"><span>优先动作</span><span>重发事件、核对下游系统状态</span></div>
              <div class="ops-row"><span>重点核对</span><span>发布状态、重试次数、下次重试时间</span></div>
              <div class="ops-row"><span>典型场景</span><span>下游未消费、消息投递失败、补偿事件积压</span></div>
            </div>
          </div>
          <div v-else class="state-box">选择左侧支付事件后，可在这里查看事件快照与排障建议。</div>
        </aside>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条支付事件</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 360px;
  gap: 16px;
}

.detail-side {
  display: grid;
  align-self: start;
}

.detail-stack {
  display: grid;
  gap: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.detail-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.detail-card strong {
  color: #0f172a;
  word-break: break-word;
}

.detail-card-wide {
  grid-column: 1 / -1;
}

.ops-card {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #dbe3f0;
}

.ops-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.ops-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #dbe3f0;
}

.ops-row:last-child {
  border-bottom: 0;
}
</style>
