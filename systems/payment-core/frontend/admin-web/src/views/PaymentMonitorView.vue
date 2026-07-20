<script setup>
import { onMounted, ref } from "vue";
import { paymentMonitorApi } from "../api/client";

const overview = ref({
  trends: [],
  channelMetrics: [],
  alerts: []
});
const isLoading = ref(true);
const errorMessage = ref("");

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    overview.value = await paymentMonitorApi.getOverview();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadOverview);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付监控分析</h2>
        <p>查看最近支付趋势、渠道表现和待处理异常</p>
      </div>
      <button class="button secondary" @click="loadOverview">刷新</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付监控数据加载失败：{{ errorMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付监控数据加载中...</div>

      <template v-else>
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">最近趋势天数</div>
            <div class="detail-value">{{ overview.trends.length }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">渠道监控维度</div>
            <div class="detail-value">{{ overview.channelMetrics.length }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">异常告警数</div>
            <div class="detail-value">{{ overview.alerts.length }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">监控状态</div>
            <div class="detail-value">在线</div>
          </div>
        </div>

        <div class="split-panels">
          <section class="panel mini">
            <div class="section-title">
              <h3>最近支付趋势</h3>
              <span class="meta">按日汇总</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>总单量</th>
                  <th>成功单量</th>
                  <th>成功金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in overview.trends" :key="item.statDate">
                  <td>{{ item.statDate }}</td>
                  <td>{{ item.totalCount }}</td>
                  <td>{{ item.successCount }}</td>
                  <td>{{ item.successAmount }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>异常告警</h3>
              <span class="meta">优先处理</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>等级</th>
                  <th>标题</th>
                  <th>影响</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="alert in overview.alerts" :key="alert.alertType">
                  <td><span :class="['badge', alert.alertLevelType]">{{ alert.alertLevel }}</span></td>
                  <td>
                    <div>{{ alert.alertTitle }}</div>
                    <div class="meta">{{ alert.alertMessage }}</div>
                  </td>
                  <td>{{ alert.affectedCount }} 笔</td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <h3>渠道监控</h3>
            <span class="meta">按渠道和支付方式统计</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>渠道编码</th>
                <th>支付方式</th>
                <th>总单量</th>
                <th>成功单量</th>
                <th>成功率</th>
                <th>成功金额</th>
                <th>待处理</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in overview.channelMetrics" :key="item.channelCode + item.paymentMethod">
                <td>{{ item.channelCode }}</td>
                <td>{{ item.paymentMethod }}</td>
                <td>{{ item.totalCount }}</td>
                <td>{{ item.successCount }}</td>
                <td>{{ item.successRate }}</td>
                <td>{{ item.successAmount }}</td>
                <td>{{ item.pendingCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </div>
</template>
