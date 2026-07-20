<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentRequestApi } from "../api/client";

const route = useRoute();
const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const expandedRequestNo = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  requestNo: route.query.requestNo || "",
  paymentOrderId: route.query.paymentOrderId || "",
  orderNo: route.query.orderNo || "",
  channelCode: route.query.channelCode || "",
  terminal: route.query.terminal || "全部",
  clientIp: route.query.clientIp || "",
  requestStatus: route.query.requestStatus || "全部",
  sortField: route.query.sortField || "createdAt",
  sortOrder: route.query.sortOrder || "desc"
});

function resetFilters() {
  filters.value = {
    requestNo: "",
    paymentOrderId: "",
    orderNo: "",
    channelCode: "",
    terminal: "全部",
    clientIp: "",
    requestStatus: "全部",
    sortField: "createdAt",
    sortOrder: "desc"
  };
  expandedRequestNo.value = "";
  pageNo.value = 1;
  loadPaymentRequests();
}

function togglePayload(requestNo) {
  expandedRequestNo.value = expandedRequestNo.value === requestNo ? "" : requestNo;
}

async function loadPaymentRequests() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentRequestApi.getList({
      requestNo: filters.value.requestNo,
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      channelCode: filters.value.channelCode,
      terminal: filters.value.terminal,
      clientIp: filters.value.clientIp,
      requestStatus: filters.value.requestStatus,
      sortField: filters.value.sortField,
      sortOrder: filters.value.sortOrder,
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

function applyFilters() {
  expandedRequestNo.value = "";
  pageNo.value = 1;
  loadPaymentRequests();
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  expandedRequestNo.value = "";
  loadPaymentRequests();
}

onMounted(loadPaymentRequests);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付请求管理</h2>
        <p>查看支付尝试的请求报文、响应报文、渠道和路由结果，支撑联调与问题定位</p>
      </div>
      <button class="button primary">导出请求</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付请求数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>请求编号</label>
          <input v-model="filters.requestNo" placeholder="请输入支付请求编号" />
        </div>
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>渠道编码</label>
          <input v-model="filters.channelCode" placeholder="如 wx_h5 / alipay_h5" />
        </div>
        <div class="field">
          <label>终端</label>
          <select v-model="filters.terminal">
            <option>全部</option>
            <option>H5</option>
            <option>PC</option>
            <option>APP</option>
            <option>小程序</option>
          </select>
        </div>
        <div class="field">
          <label>客户端 IP</label>
          <input v-model="filters.clientIp" placeholder="如 127.0.0.1" />
        </div>
        <div class="field">
          <label>请求状态</label>
          <select v-model="filters.requestStatus">
            <option>全部</option>
            <option>处理中</option>
            <option>等待回调</option>
            <option>成功</option>
            <option>已关闭</option>
          </select>
        </div>
        <div class="field">
          <label>排序字段</label>
          <select v-model="filters.sortField">
            <option value="createdAt">创建时间</option>
            <option value="channelCode">渠道编码</option>
            <option value="terminal">终端</option>
          </select>
        </div>
        <div class="field">
          <label>排序方向</label>
          <select v-model="filters.sortOrder">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="已支持订单号、渠道、终端、IP 筛选与排序，生产环境需继续脱敏和权限控制" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付请求数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付请求</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>请求编号</th>
              <th>支付单号</th>
              <th>预付单号</th>
              <th>订单号</th>
              <th>支付方式</th>
              <th>渠道编码</th>
              <th>路由结果</th>
              <th>终端</th>
              <th>客户端 IP</th>
              <th>幂等键</th>
              <th>请求状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in items" :key="item.requestNo">
              <tr>
                <td>{{ item.requestNo }}</td>
                <td>
                  <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                    {{ item.paymentOrderId }}
                  </RouterLink>
                </td>
                <td>{{ item.prepayOrderNo }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.paymentMethod }}</td>
                <td>{{ item.channelCode }}</td>
                <td>{{ item.routeResult || "-" }}</td>
                <td>{{ item.terminal || "-" }}</td>
                <td>{{ item.clientIp || "-" }}</td>
                <td>{{ item.idempotencyKey || "-" }}</td>
                <td><span :class="['badge', item.requestStatusType]">{{ item.requestStatus }}</span></td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <RouterLink class="link-button" :to="`/payments/${item.paymentOrderId}`">
                    查看支付单
                  </RouterLink>
                  <button class="link-button" @click="togglePayload(item.requestNo)">
                    {{ expandedRequestNo === item.requestNo ? "收起报文" : "查看报文" }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedRequestNo === item.requestNo">
                <td colspan="13">
                  <div class="payload-grid">
                    <div>
                      <strong>请求报文</strong>
                      <pre>{{ item.requestPayload }}</pre>
                    </div>
                    <div>
                      <strong>响应报文</strong>
                      <pre>{{ item.responsePayload || "-" }}</pre>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <div class="pager">
        <span>共 {{ total }} 条支付请求</span>
        <template v-if="total > pageSize">
          <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
          <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
        </template>
      </div>
    </section>
  </div>
</template>
