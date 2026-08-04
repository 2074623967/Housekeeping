<script setup>
import { onMounted, ref } from "vue";
import { opsConfigApi } from "./api/client";

const summary = ref({ metrics: [], highlights: [] });
const agreementTemplates = ref([]);
const businessLines = ref([]);
const paymentTypes = ref([]);
const cashierTemplates = ref([]);
const channelProfiles = ref([]);
const routingRules = ref([]);
const systemControls = ref([]);
const loading = ref(true);
const message = ref("");
const activeCode = ref("");

async function loadAll() {
  loading.value = true;
  message.value = "";
  try {
    const [summaryData, agreementData, businessData, paymentTypeData, cashierData, channelData, routeData, controlData] = await Promise.all([
      opsConfigApi.getSummary(),
      opsConfigApi.getAgreementTemplates(),
      opsConfigApi.getBusinessLines(),
      opsConfigApi.getPaymentTypes(),
      opsConfigApi.getCashierTemplates(),
      opsConfigApi.getChannelProfiles(),
      opsConfigApi.getRoutingRules(),
      opsConfigApi.getSystemControls()
    ]);
    summary.value = summaryData;
    agreementTemplates.value = agreementData.records;
    businessLines.value = businessData.records;
    paymentTypes.value = paymentTypeData.records;
    cashierTemplates.value = cashierData.records;
    channelProfiles.value = channelData.records;
    routingRules.value = routeData.records;
    systemControls.value = controlData.records;
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

onMounted(loadAll);
</script>

<template>
  <div class="page">
    <header class="hero">
      <div>
        <p class="eyebrow">ops-config-system</p>
        <h1>支付运营配置中心</h1>
        <p class="lead">承接协议、业务线、支付类型、收银台模板、渠道档案、路由规则和系统控制的统一运营配置。</p>
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
        <h2>配置域说明</h2>
        <ul>
          <li v-for="item in summary.highlights" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="grid">
        <article class="card">
          <h2>协议模板</h2>
          <table>
            <thead><tr><th>编码</th><th>模板</th><th>主体</th><th>签约要素</th><th>签章服务</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in agreementTemplates" :key="item.templateCode">
                <td>{{ item.templateCode }}</td><td>{{ item.templateName }}</td><td>{{ item.subjectType }}</td><td>{{ item.signFields }}</td><td>{{ item.esignProvider }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.templateCode" @click="toggle(opsConfigApi.toggleAgreementTemplate, item.templateCode, item.status !== 'ENABLED', '协议模板')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="card">
          <h2>业务线</h2>
          <table>
            <thead><tr><th>编码</th><th>名称</th><th>场景</th><th>负责人</th><th>结算策略</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in businessLines" :key="item.businessCode">
                <td>{{ item.businessCode }}</td><td>{{ item.businessName }}</td><td>{{ item.defaultScene }}</td><td>{{ item.owner }}</td><td>{{ item.settlementPolicy }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.businessCode" @click="toggle(opsConfigApi.toggleBusinessLine, item.businessCode, item.status !== 'ENABLED', '业务线')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <section class="grid">
        <article class="card">
          <h2>支付类型</h2>
          <table>
            <thead><tr><th>编码</th><th>名称</th><th>交易大类</th><th>计费口径</th><th>退款能力</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in paymentTypes" :key="item.typeCode">
                <td>{{ item.typeCode }}</td><td>{{ item.typeName }}</td><td>{{ item.transactionCategory }}</td><td>{{ item.feePolicy }}</td><td>{{ item.refundCapability }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.typeCode" @click="toggle(opsConfigApi.togglePaymentType, item.typeCode, item.status !== 'ENABLED', '支付类型')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="card">
          <h2>收银台模板</h2>
          <table>
            <thead><tr><th>编码</th><th>模板</th><th>终端</th><th>默认支付方式</th><th>展示策略</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in cashierTemplates" :key="item.templateCode">
                <td>{{ item.templateCode }}</td><td>{{ item.templateName }}</td><td>{{ item.terminalType }}</td><td>{{ item.defaultPayMethod }}</td><td>{{ item.displayPolicy }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.templateCode" @click="toggle(opsConfigApi.toggleCashierTemplate, item.templateCode, item.status !== 'ENABLED', '收银台模板')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <section class="grid">
        <article class="card">
          <h2>渠道档案</h2>
          <table>
            <thead><tr><th>编码</th><th>渠道</th><th>类型</th><th>商户模板</th><th>退款时效</th><th>风控标签</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in channelProfiles" :key="item.channelCode">
                <td>{{ item.channelCode }}</td><td>{{ item.channelName }}</td><td>{{ item.channelType }}</td><td>{{ item.merchantProfile }}</td><td>{{ item.refundSla }}</td><td>{{ item.riskTag }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.channelCode" @click="toggle(opsConfigApi.toggleChannelProfile, item.channelCode, item.status !== 'ENABLED', '渠道档案')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="card">
          <h2>路由规则</h2>
          <table>
            <thead><tr><th>编码</th><th>业务线</th><th>支付类型</th><th>优先渠道</th><th>备选渠道</th><th>命中策略</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in routingRules" :key="item.routeCode">
                <td>{{ item.routeCode }}</td><td>{{ item.businessCode }}</td><td>{{ item.payType }}</td><td>{{ item.primaryChannel }}</td><td>{{ item.backupChannel }}</td><td>{{ item.matchPolicy }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td><button class="link" :disabled="activeCode === item.routeCode" @click="toggle(opsConfigApi.toggleRoutingRule, item.routeCode, item.status !== 'ENABLED', '路由规则')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <section class="card">
        <h2>系统控制</h2>
        <table>
          <thead><tr><th>编码</th><th>控制名称</th><th>范围</th><th>控制值</th><th>风险级别</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in systemControls" :key="item.controlCode">
              <td>{{ item.controlCode }}</td><td>{{ item.controlName }}</td><td>{{ item.controlScope }}</td><td>{{ item.controlValue }}</td><td>{{ item.riskLevel }}</td>
              <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
              <td><button class="link" :disabled="activeCode === item.controlCode" @click="toggle(opsConfigApi.toggleSystemControl, item.controlCode, item.status !== 'ENABLED', '系统控制')">{{ item.status === "ENABLED" ? "停用" : "启用" }}</button></td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
