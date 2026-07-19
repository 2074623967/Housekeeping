<script setup>
import { onMounted, ref } from "vue";
import { paymentApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const detail = ref(null);

async function openDetail(paymentOrderId) {
  try {
    detail.value = await paymentApi.getDetail(paymentOrderId);
  } catch (error) {
    errorMessage.value = error.message;
  }
}

async function handleQuery(paymentOrderId) {
  detail.value = await paymentApi.query(paymentOrderId);
}

async function handleClose(paymentOrderId) {
  detail.value = await paymentApi.close(paymentOrderId);
}

async function handleCallback(paymentOrderId) {
  detail.value = await paymentApi.callback("wx_jsapi", paymentOrderId);
}

onMounted(async () => {
  try {
    items.value = await paymentApi.getList();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付单管理</h2>
        <p>统一查看支付单、支付方式、渠道交易和回调收敛情况</p>
      </div>
      <button class="button primary">导出支付单</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付单数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select>
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行卡</option>
          </select>
        </div>
        <div class="field">
          <label>支付状态</label>
          <select>
            <option>全部</option>
            <option>SUCCESS</option>
            <option>WAIT_CALLBACK</option>
            <option>FAIL</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary">查询</button>
          <button class="button secondary">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付单数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付单数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>支付单号</th>
              <th>订单号</th>
              <th>用户</th>
              <th>实付金额</th>
              <th>支付方式</th>
              <th>支付渠道</th>
              <th>渠道交易号</th>
              <th>支付状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.paymentOrderId">
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.amount }}</td>
              <td>{{ item.paymentMethod }}</td>
              <td>{{ item.channel }}</td>
              <td>{{ item.channelTransactionNo }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td>{{ item.createdAt }}</td>
              <td>
                <div class="list-actions">
                  <button class="link-button" @click="openDetail(item.paymentOrderId)">详情</button>
                  <button class="link-button" @click="handleQuery(item.paymentOrderId)">查单</button>
                  <button class="link-button" @click="handleCallback(item.paymentOrderId)">回调</button>
                  <button class="link-button" @click="handleClose(item.paymentOrderId)">关闭</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="detail" class="detail-panel">
        <div class="section-title">
          <h3>支付单详情</h3>
          <span class="meta">{{ detail.paymentOrderId }}</span>
        </div>
        <div class="detail-grid">
          <div><strong>预付单号：</strong>{{ detail.prepayOrderNo }}</div>
          <div><strong>账单号：</strong>{{ detail.billNo }}</div>
          <div><strong>订单号：</strong>{{ detail.orderNo }}</div>
          <div><strong>金额：</strong>{{ detail.amount }}</div>
          <div><strong>方式：</strong>{{ detail.paymentMethod }}</div>
          <div><strong>渠道：</strong>{{ detail.channel }}</div>
          <div><strong>状态：</strong>{{ detail.status }}</div>
          <div><strong>创建时间：</strong>{{ detail.createdAt }}</div>
        </div>
        <div class="split-panels">
          <section class="panel mini">
            <h4>路由记录</h4>
            <div v-for="item in detail.routeLogs || []" :key="item">{{ item }}</div>
          </section>
          <section class="panel mini">
            <h4>回调日志</h4>
            <div v-for="item in detail.notifyLogs || []" :key="item">{{ item }}</div>
          </section>
        </div>
        <section class="panel mini">
          <h4>事件轨迹</h4>
          <div v-for="item in detail.eventLogs || []" :key="item">{{ item }}</div>
        </section>
      </div>
    </section>
  </div>
</template>
