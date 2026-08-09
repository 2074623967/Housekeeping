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
const configChanges = ref([]);
const changeForm = ref({
  configType: "ROUTING_RULE",
  configCode: "ROUTE_HOME_DEPOSIT",
  enabled: true,
  reason: "",
  grayScope: "ALL",
  grayStartAt: "",
  grayEndAt: "",
  applicant: "ops-owner"
});
const actionUser = ref("ops-owner");
const snapshot = ref(null);
const loading = ref(true);
const message = ref("");
const activeCode = ref("");
const snapshotQuery = ref({
  businessCode: "HOME_CLEAN",
  payType: "PAY_CONSUME",
  terminalType: "APP"
});

async function loadAll() {
  loading.value = true;
  message.value = "";
  try {
    const [summaryData, agreementData, businessData, paymentTypeData, cashierData, channelData, routeData, controlData, changeData] = await Promise.all([
      opsConfigApi.getSummary(),
      opsConfigApi.getAgreementTemplates(),
      opsConfigApi.getBusinessLines(),
      opsConfigApi.getPaymentTypes(),
      opsConfigApi.getCashierTemplates(),
      opsConfigApi.getChannelProfiles(),
      opsConfigApi.getRoutingRules(),
      opsConfigApi.getSystemControls(),
      opsConfigApi.getConfigChanges()
    ]);
    summary.value = summaryData;
    agreementTemplates.value = agreementData.records;
    businessLines.value = businessData.records;
    paymentTypes.value = paymentTypeData.records;
    cashierTemplates.value = cashierData.records;
    channelProfiles.value = channelData.records;
    routingRules.value = routeData.records;
    systemControls.value = controlData.records;
    configChanges.value = changeData.records;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createConfigChange() {
  try {
    const created = await opsConfigApi.createConfigChange(changeForm.value);
    configChanges.value = [created, ...configChanges.value];
    changeForm.value.reason = "";
    message.value = `${created.requestNo} 已进入待审批`;
  } catch (error) {
    message.value = `创建配置变更失败：${error.message}`;
  }
}

async function approveConfigChange(change, approved) {
  try {
    const updated = await opsConfigApi.approveConfigChange({
      requestNo: change.requestNo,
      approved,
      approver: actionUser.value,
      approvalComment: approved ? "审批通过，允许按灰度窗口发布" : "审批拒绝"
    });
    configChanges.value = configChanges.value.map((item) => item.requestNo === updated.requestNo ? updated : item);
    message.value = `${updated.requestNo} 已${approved ? "审批通过" : "拒绝"}`;
  } catch (error) {
    message.value = `审批失败：${error.message}`;
  }
}

async function publishConfigChange(change) {
  try {
    const updated = await opsConfigApi.publishConfigChange({
      requestNo: change.requestNo,
      operator: actionUser.value
    });
    configChanges.value = configChanges.value.map((item) => item.requestNo === updated.requestNo ? updated : item);
    await loadAll();
    message.value = `${updated.requestNo} 已发布`;
  } catch (error) {
    message.value = `发布失败：${error.message}`;
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

async function loadSnapshot() {
  try {
    snapshot.value = await opsConfigApi.getEffectiveSnapshot(snapshotQuery.value);
    message.value = "有效配置快照已加载";
  } catch (error) {
    message.value = error.message;
  }
}

async function exportSnapshot() {
  try {
    const blob = await opsConfigApi.downloadEffectiveSnapshot(snapshotQuery.value);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "ops-config-effective-snapshot.csv";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    message.value = "有效配置快照已导出";
  } catch (error) {
    message.value = `有效配置快照导出失败：${error.message}`;
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

      <section class="card">
        <h2>有效配置快照</h2>
        <div class="toolbar">
          <input v-model="snapshotQuery.businessCode" placeholder="业务线编码" />
          <input v-model="snapshotQuery.payType" placeholder="支付类型编码" />
          <input v-model="snapshotQuery.terminalType" placeholder="终端类型" />
          <button class="button secondary" @click="loadSnapshot">查询快照</button>
          <button class="button secondary" @click="exportSnapshot">导出快照</button>
        </div>
        <div v-if="snapshot" class="banner" style="margin-top: 12px">
          {{ snapshot.businessCode }} / {{ snapshot.payType }} / {{ snapshot.terminalType }}，默认支付方式：{{ snapshot.defaultPayMethod }}，路由：{{ snapshot.routeMatchPolicy }}
        </div>
      </section>

      <section class="card">
        <div class="section-heading">
          <div>
            <h2>配置版本与灰度发布</h2>
            <p class="muted">启停变更先申请、再审批，只有审批通过且处于灰度时间窗内才会修改生效配置。</p>
          </div>
        </div>
        <div class="change-form">
          <select v-model="changeForm.configType">
            <option value="AGREEMENT_TEMPLATE">协议模板</option>
            <option value="BUSINESS_LINE">业务线</option>
            <option value="PAYMENT_TYPE">支付类型</option>
            <option value="CASHIER_TEMPLATE">收银台模板</option>
            <option value="CHANNEL_PROFILE">渠道档案</option>
            <option value="ROUTING_RULE">路由规则</option>
            <option value="SYSTEM_CONTROL">系统控制</option>
          </select>
          <input v-model="changeForm.configCode" placeholder="配置编码" />
          <select v-model="changeForm.enabled">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
          <input v-model="changeForm.grayScope" placeholder="灰度范围，例如 ALL" />
          <input v-model="changeForm.grayStartAt" placeholder="开始时间 yyyy-MM-dd HH:mm:ss（可选）" />
          <input v-model="changeForm.grayEndAt" placeholder="结束时间 yyyy-MM-dd HH:mm:ss（可选）" />
          <input v-model="changeForm.reason" placeholder="变更原因" />
          <input v-model="changeForm.applicant" placeholder="申请人" />
          <button class="button" @click="createConfigChange">提交审批</button>
        </div>
        <div class="toolbar">
          <label class="muted">操作人</label>
          <input v-model="actionUser" placeholder="审批/发布操作人" />
        </div>
        <table>
          <thead><tr><th>申请号</th><th>配置</th><th>目标状态</th><th>灰度范围</th><th>申请人</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="change in configChanges" :key="change.requestNo">
              <td>{{ change.requestNo }}</td>
              <td>{{ change.configType }} / {{ change.configCode }}</td>
              <td>{{ change.targetStatus }}</td>
              <td>{{ change.grayScope }}<br />{{ change.grayStartAt || "立即" }} - {{ change.grayEndAt || "不限" }}</td>
              <td>{{ change.applicant }}</td>
              <td><span class="tag" :class="change.statusType">{{ change.status }}</span></td>
              <td class="actions">
                <button v-if="change.status === 'PENDING_APPROVAL'" class="link" @click="approveConfigChange(change, true)">通过</button>
                <button v-if="change.status === 'PENDING_APPROVAL'" class="link danger-link" @click="approveConfigChange(change, false)">拒绝</button>
                <button v-if="change.status === 'APPROVED'" class="link" @click="publishConfigChange(change)">发布</button>
              </td>
            </tr>
            <tr v-if="configChanges.length === 0"><td colspan="7" class="empty">暂无配置变更申请</td></tr>
          </tbody>
        </table>
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
