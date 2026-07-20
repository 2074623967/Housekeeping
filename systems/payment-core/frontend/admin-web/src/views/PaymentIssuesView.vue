<script setup>
import { onMounted, ref } from "vue";
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
const filters = ref({
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  issueType: route.query.issueType || "全部",
  severity: route.query.severity || "全部",
  channelCode: route.query.channelCode || "",
  paymentMethod: route.query.paymentMethod || "全部"
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

      <div v-if="isLoading" class="state-box">支付交易异常加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付交易异常</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
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
              <th>异常时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.issueNo">
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
