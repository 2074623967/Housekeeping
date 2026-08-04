<script setup>
import { onMounted, ref } from "vue";
import { riskControlApi } from "./api/client";

const summary = ref({ metrics: [], highlights: [] });
const policies = ref([]);
const limitRules = ref([]);
const blocklists = ref([]);
const interceptEvents = ref([]);
const reviewOrders = ref([]);
const monitorRules = ref([]);
const loading = ref(true);
const message = ref("");
const activeCode = ref("");

async function loadAll() {
  loading.value = true;
  message.value = "";
  try {
    const [summaryData, policyData, limitData, blockData, eventData, reviewData, monitorData] = await Promise.all([
      riskControlApi.getSummary(),
      riskControlApi.getPolicies(),
      riskControlApi.getLimitRules(),
      riskControlApi.getBlocklists(),
      riskControlApi.getInterceptEvents(),
      riskControlApi.getReviewOrders(),
      riskControlApi.getMonitorRules()
    ]);
    summary.value = summaryData;
    policies.value = policyData.records;
    limitRules.value = limitData.records;
    blocklists.value = blockData.records;
    interceptEvents.value = eventData.records;
    reviewOrders.value = reviewData.records;
    monitorRules.value = monitorData.records;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function toggle(run, code, enabled, label) {
  activeCode.value = code;
  try {
    summary.value = await run(code, enabled);
    await loadAll();
    message.value = `${label} ${code} 已${enabled ? "启用" : "停用"}`;
  } catch (error) {
    message.value = `${label} ${code} 操作失败：${error.message}`;
  } finally {
    activeCode.value = "";
  }
}

async function review(reviewNo, action) {
  activeCode.value = reviewNo;
  try {
    const result = await riskControlApi.reviewAction(reviewNo, action, action === "APPROVE" ? "风控审核通过" : "风控审核拒绝");
    reviewOrders.value = result.records;
    await loadAll();
    message.value = `复核单 ${reviewNo} 已${action === "APPROVE" ? "通过" : "拒绝"}`;
  } catch (error) {
    message.value = `复核单 ${reviewNo} 处理失败：${error.message}`;
  } finally {
    activeCode.value = "";
  }
}

onMounted(loadAll);
</script>

<template>
  <div class="page">
    <header class="hero">
      <div>
        <p class="eyebrow">risk-control-system</p>
        <h1>支付风控中心</h1>
        <p class="lead">统一承接限额、拦截、黑名单、人工复核和风险监控，不再继续挤在 payment-core 的过渡页面里。</p>
      </div>
      <button class="button" @click="loadAll">刷新</button>
    </header>

    <section v-if="message" class="banner">{{ message }}</section>
    <section v-if="loading" class="card">加载中...</section>

    <template v-else>
      <section class="metrics">
        <article v-for="metric in summary.metrics" :key="metric.title" class="metric">
          <span class="tag" :class="metric.badgeType">{{ metric.badgeText }}</span>
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.title }}</span>
        </article>
      </section>

      <section class="card">
        <h2>风控域说明</h2>
        <ul>
          <li v-for="item in summary.highlights" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="grid">
        <article class="card">
          <h2>风险策略</h2>
          <table>
            <thead><tr><th>编码</th><th>策略</th><th>维度</th><th>动作</th><th>等级</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in policies" :key="item.policyCode">
                <td>{{ item.policyCode }}</td><td>{{ item.policyName }}</td><td>{{ item.riskDimension }}</td><td>{{ item.hitAction }}</td><td>{{ item.riskLevel }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.policyCode" @click="toggle(riskControlApi.togglePolicy, item.policyCode, item.status !== 'ENABLED', '风险策略')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="card">
          <h2>限额规则</h2>
          <table>
            <thead><tr><th>编码</th><th>规则</th><th>对象</th><th>场景</th><th>限额</th><th>时间窗</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in limitRules" :key="item.ruleCode">
                <td>{{ item.ruleCode }}</td><td>{{ item.ruleName }}</td><td>{{ item.targetType }}</td><td>{{ item.sceneCode }}</td><td>{{ item.limitValue }}</td><td>{{ item.timeWindow }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.ruleCode" @click="toggle(riskControlApi.toggleLimitRule, item.ruleCode, item.status !== 'ENABLED', '限额规则')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <section class="grid">
        <article class="card">
          <h2>黑名单</h2>
          <table>
            <thead><tr><th>编码</th><th>主体</th><th>类型</th><th>原因</th><th>动作</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in blocklists" :key="item.blockCode">
                <td>{{ item.blockCode }}</td><td>{{ item.subjectValue }}</td><td>{{ item.subjectType }}</td><td>{{ item.reason }}</td><td>{{ item.actionType }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.blockCode" @click="toggle(riskControlApi.toggleBlocklist, item.blockCode, item.status !== 'ENABLED', '黑名单')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="card">
          <h2>监控规则</h2>
          <table>
            <thead><tr><th>编码</th><th>规则</th><th>对象</th><th>阈值</th><th>通知</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in monitorRules" :key="item.monitorCode">
                <td>{{ item.monitorCode }}</td><td>{{ item.monitorName }}</td><td>{{ item.monitorTarget }}</td><td>{{ item.alertThreshold }}</td><td>{{ item.notifyPolicy }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.monitorCode" @click="toggle(riskControlApi.toggleMonitorRule, item.monitorCode, item.status !== 'ENABLED', '监控规则')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <section class="grid">
        <article class="card">
          <h2>风险拦截事件</h2>
          <table>
            <thead><tr><th>事件号</th><th>支付单</th><th>命中策略</th><th>风险等级</th><th>结果</th><th>来源</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="item in interceptEvents" :key="item.eventNo">
                <td>{{ item.eventNo }}</td><td>{{ item.paymentOrderId }}</td><td>{{ item.hitPolicy }}</td><td>{{ item.riskLevel }}</td><td>{{ item.decisionResult }}</td><td>{{ item.sourceSystem }}</td><td>{{ item.happenedAt }}</td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="card">
          <h2>人工复核单</h2>
          <table>
            <thead><tr><th>复核单号</th><th>业务单号</th><th>风险标签</th><th>事项</th><th>状态</th><th>审核人</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in reviewOrders" :key="item.reviewNo">
                <td>{{ item.reviewNo }}</td><td>{{ item.businessNo }}</td><td>{{ item.riskTag }}</td><td>{{ item.reviewItem }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td><td>{{ item.reviewer || "-" }}</td>
                <td>
                  <template v-if="item.status === 'PENDING'">
                    <button class="link" :disabled="activeCode === item.reviewNo" @click="review(item.reviewNo, 'APPROVE')">通过</button>
                    <button class="link danger-link" :disabled="activeCode === item.reviewNo" @click="review(item.reviewNo, 'REJECT')">拒绝</button>
                  </template>
                  <span v-else>已处理</span>
                </td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>
    </template>
  </div>
</template>

