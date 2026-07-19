<script setup>
import { onMounted, ref } from "vue";
import { dashboardApi } from "../api/client";

const summary = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");

onMounted(async () => {
  try {
    const data = await dashboardApi.getSummary();
    summary.value = data.cards;
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
        <h2>工作台</h2>
        <p>查看家政支付系统当天核心指标和风险提醒</p>
      </div>
      <span class="badge danger">阻断级差异 2 笔</span>
    </div>

    <div class="risk-banner">
      当前存在 2 笔高优先级资金差异，1 笔线下汇入待人工审核，请财务和运营优先处理。
    </div>

    <div v-if="errorMessage" class="error-banner">
      工作台数据加载失败：{{ errorMessage }}
    </div>

    <div v-if="isLoading" class="state-box">工作台数据加载中...</div>

    <div v-else class="card-grid">
      <div v-for="card in summary" :key="card.key" class="card">
        <p class="card-title">{{ card.title }}</p>
        <p class="card-value">{{ card.value }}</p>
        <span :class="['badge', card.badgeType]">{{ card.badgeText }}</span>
      </div>
    </div>

    <div v-if="!isLoading" class="split-panels">
      <section class="panel">
        <div class="section-title">
          <h3>今日重点事项</h3>
          <span class="meta">按优先级排序</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>事项</th>
              <th>责任角色</th>
              <th>影响范围</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>线下汇入支付单待人工确认</td>
              <td>资金</td>
              <td>企业保洁订单</td>
              <td><span class="badge warn">待审核</span></td>
            </tr>
            <tr>
              <td>服务者结算批次待提交</td>
              <td>财务</td>
              <td>华东大区</td>
              <td><span class="badge info">待处理</span></td>
            </tr>
            <tr>
              <td>保证金扣罚审批待复核</td>
              <td>审计</td>
              <td>保洁业务线</td>
              <td><span class="badge warn">待复核</span></td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="panel">
        <div class="section-title">
          <h3>今日健康度</h3>
          <span class="meta">实时汇总</span>
        </div>
        <table>
          <tbody>
            <tr>
              <th>支付成功率</th>
              <td>98.74%</td>
            </tr>
            <tr>
              <th>退款成功率</th>
              <td>96.22%</td>
            </tr>
            <tr>
              <th>服务者结算准时率</th>
              <td>94.10%</td>
            </tr>
            <tr>
              <th>资金日报完成度</th>
              <td>83.00%</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  </div>
</template>
