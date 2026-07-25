<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentIssueApi } from "../api/client";

const route = useRoute();
const items = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const isLoading = ref(true);
const errorMessage = ref("");
const filters = ref({
  alertNo: route.query.alertNo || "",
  issueNo: route.query.issueNo || "",
  paymentOrderId: route.query.paymentOrderId || "",
  alertChannel: route.query.alertChannel || "全部",
  alertStatus: route.query.alertStatus || "全部",
  ackStatus: route.query.ackStatus || "全部",
  providerDeliveryStatus: route.query.providerDeliveryStatus || "全部"
});

function resetFilters() {
  filters.value = {
    alertNo: "",
    issueNo: "",
    paymentOrderId: "",
    alertChannel: "全部",
    alertStatus: "全部",
    ackStatus: "全部",
    providerDeliveryStatus: "全部"
  };
  pageNo.value = 1;
  loadAlertLogs();
}

function applyFilters() {
  pageNo.value = 1;
  loadAlertLogs();
}

async function loadAlertLogs() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentIssueApi.getAlertLogs({
      alertNo: filters.value.alertNo,
      issueNo: filters.value.issueNo,
      paymentOrderId: filters.value.paymentOrderId,
      alertChannel: filters.value.alertChannel,
      alertStatus: filters.value.alertStatus,
      ackStatus: filters.value.ackStatus,
      providerDeliveryStatus: filters.value.providerDeliveryStatus,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
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
  loadAlertLogs();
}

onMounted(loadAlertLogs);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>异常告警明细台</h2>
        <p>统一联查 outbox、外部通知通道、供应商投递回执和人工确认状态，方便运营、研发与测试核对异常触达闭环</p>
      </div>
      <button class="button secondary" @click="loadAlertLogs">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        异常告警明细加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>告警编号</label>
          <input v-model="filters.alertNo" placeholder="请输入告警编号" />
        </div>
        <div class="field">
          <label>异常编号</label>
          <input v-model="filters.issueNo" placeholder="请输入异常编号" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>告警通道</label>
          <select v-model="filters.alertChannel">
            <option>全部</option>
            <option>IN_APP_OUTBOX</option>
            <option>IM</option>
            <option>SMS</option>
            <option>EMAIL</option>
          </select>
        </div>
        <div class="field">
          <label>告警状态</label>
          <select v-model="filters.alertStatus">
            <option>全部</option>
            <option>已生成</option>
            <option>已派发</option>
            <option>部分失败</option>
            <option>派发失败</option>
          </select>
        </div>
        <div class="field">
          <label>回执状态</label>
          <select v-model="filters.ackStatus">
            <option>全部</option>
            <option>待确认</option>
            <option>已确认</option>
            <option>无需回执</option>
          </select>
        </div>
        <div class="field">
          <label>供应商投递状态</label>
          <select v-model="filters.providerDeliveryStatus">
            <option>全部</option>
            <option>ACCEPTED</option>
            <option>DELIVERED</option>
            <option>CONFIG_MISSING</option>
            <option>NOTIFIER_MISSING</option>
            <option>SEND_EXCEPTION</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">异常告警明细加载中...</div>
      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的异常告警明细</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>告警编号</th>
              <th>异常编号</th>
              <th>支付单号</th>
              <th>异常类型</th>
              <th>严重等级</th>
              <th>责任组</th>
              <th>通道/接收人</th>
              <th>告警状态</th>
              <th>回执状态</th>
              <th>供应商信息</th>
              <th>投递回执</th>
              <th>告警内容</th>
              <th>渲染快照</th>
              <th>触发来源</th>
              <th>确认信息</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.alertNo">
              <td>{{ item.alertNo }}</td>
              <td>{{ item.issueNo }}</td>
              <td>
                <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                  {{ item.paymentOrderId }}
                </RouterLink>
              </td>
              <td>{{ item.issueType }}</td>
              <td>{{ item.severity }}</td>
              <td>{{ item.responsibilityGroup }}</td>
              <td>
                <div>{{ item.alertChannel }}</div>
                <div class="muted-text">{{ item.receiver }}</div>
              </td>
              <td><span :class="['badge', item.alertStatusType]">{{ item.alertStatus }}</span></td>
              <td><span :class="['badge', item.ackStatusType]">{{ item.ackStatus }}</span></td>
              <td class="flow-summary-cell">
                <div>{{ item.providerName || item.providerCode || "-" }}</div>
                <div class="muted-text">{{ item.endpointAlias || "-" }}</div>
                <div class="muted-text">{{ item.templateCode || "-" }}</div>
              </td>
              <td class="flow-summary-cell">
                <div>{{ item.providerDeliveryStatus || "-" }}</div>
                <div class="muted-text">{{ item.providerReceiptNo || "-" }}</div>
                <div class="muted-text">{{ item.providerDeliveryMessage || "-" }}</div>
              </td>
              <td class="flow-summary-cell">{{ item.alertContent }}</td>
              <td class="flow-summary-cell">{{ item.renderedContentSnapshot || "-" }}</td>
              <td>{{ item.triggeredBy }}</td>
              <td class="flow-summary-cell">
                <div>{{ item.ackOperator || "-" }}</div>
                <div class="muted-text">{{ item.ackAt || "未确认" }}</div>
              </td>
              <td>{{ item.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span>共 {{ total }} 条异常告警明细</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>
