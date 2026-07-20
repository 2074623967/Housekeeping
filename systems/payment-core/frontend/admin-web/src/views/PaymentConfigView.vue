<script setup>
import { onMounted, ref } from "vue";
import { paymentConfigApi } from "../api/client";

const channels = ref([]);
const routeRules = ref([]);
const protocols = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeConfigCode = ref("");

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const overview = await paymentConfigApi.getOverview();
    channels.value = overview.channels;
    routeRules.value = overview.routeRules;
    protocols.value = overview.protocols;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

async function toggleChannel(channel) {
  await toggleConfig(
    channel.channelCode,
    channel.status !== "ENABLED",
    "渠道",
    paymentConfigApi.toggleChannel
  );
}

async function toggleRouteRule(rule) {
  await toggleConfig(
    rule.ruleCode,
    rule.status !== "ENABLED",
    "路由规则",
    paymentConfigApi.toggleRouteRule
  );
}

async function toggleProtocol(protocol) {
  await toggleConfig(
    protocol.protocolCode,
    protocol.status !== "ENABLED",
    "支付协议",
    paymentConfigApi.toggleProtocol
  );
}

async function toggleConfig(configCode, enabled, configType, toggleRunner) {
  activeConfigCode.value = configCode;
  actionMessage.value = "";
  try {
    const overview = await toggleRunner(configCode, enabled);
    channels.value = overview.channels;
    routeRules.value = overview.routeRules;
    protocols.value = overview.protocols;
    actionMessage.value = `${configType} ${configCode} 已${enabled ? "启用" : "停用"}。`;
  } catch (error) {
    actionMessage.value = `${configType} ${configCode} 操作失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

onMounted(loadOverview);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付渠道与路由</h2>
        <p>管理微信、支付宝、线下银行等渠道，以及家政业务场景下的支付路由规则</p>
      </div>
      <span class="badge info">配置中心 V1</span>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付配置加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>

      <div v-if="isLoading" class="state-box">支付配置加载中...</div>

      <template v-else>
        <div class="section-title">
          <div>
            <h3>支付渠道配置</h3>
            <p class="meta">控制渠道商户号、适用场景、单日限额和启停状态</p>
          </div>
          <button class="button secondary" @click="loadOverview">刷新</button>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>渠道编码</th>
                <th>渠道名称</th>
                <th>支付方式</th>
                <th>商户号</th>
                <th>回调地址</th>
                <th>验签密钥</th>
                <th>适用场景</th>
                <th>单日限额</th>
                <th>优先级</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="channel in channels" :key="channel.channelCode">
                <td>{{ channel.channelCode }}</td>
                <td>{{ channel.channelName }}</td>
                <td>{{ channel.paymentMethod }}</td>
                <td>{{ channel.merchantNo }}</td>
                <td class="flow-summary-cell">{{ channel.callbackNotifyUrl }}</td>
                <td>{{ channel.callbackSecretMasked }}</td>
                <td>{{ channel.sceneScope }}</td>
                <td>{{ channel.dailyLimit }}</td>
                <td>{{ channel.priority }}</td>
                <td><span :class="['badge', channel.statusType]">{{ channel.status }}</span></td>
                <td>{{ channel.updatedAt }}</td>
                <td>
                  <button
                    class="link-button"
                    :disabled="activeConfigCode === channel.channelCode"
                    @click="toggleChannel(channel)"
                  >
                    {{ channel.status === "ENABLED" ? "停用" : "启用" }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>支付路由规则</h3>
              <p class="meta">按业务场景、金额、客户类型等规则决定目标渠道和兜底渠道</p>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>规则编码</th>
                  <th>规则名称</th>
                  <th>匹配场景</th>
                  <th>匹配表达式</th>
                  <th>目标渠道</th>
                  <th>兜底渠道</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="rule in routeRules" :key="rule.ruleCode">
                  <td>{{ rule.ruleCode }}</td>
                  <td>{{ rule.ruleName }}</td>
                  <td>{{ rule.matchScene }}</td>
                  <td class="flow-summary-cell">{{ rule.matchExpression }}</td>
                  <td>{{ rule.targetChannelCode }}</td>
                  <td>{{ rule.fallbackChannelCode }}</td>
                  <td>{{ rule.priority }}</td>
                  <td><span :class="['badge', rule.statusType]">{{ rule.status }}</span></td>
                  <td>{{ rule.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === rule.ruleCode"
                      @click="toggleRouteRule(rule)"
                    >
                      {{ rule.status === "ENABLED" ? "停用" : "启用" }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>支付协议管理</h3>
              <p class="meta">维护签约协议、预授权协议和代扣协议模板，控制场景适用范围与启停状态</p>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>协议编码</th>
                  <th>协议名称</th>
                  <th>协议类型</th>
                  <th>模板版本</th>
                  <th>签约模式</th>
                  <th>适用场景</th>
                  <th>适用渠道</th>
                  <th>商户确认</th>
                  <th>风控标签</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="protocol in protocols" :key="protocol.protocolCode">
                  <td>{{ protocol.protocolCode }}</td>
                  <td>{{ protocol.protocolName }}</td>
                  <td>{{ protocol.protocolType }}</td>
                  <td>{{ protocol.templateVersion }}</td>
                  <td>{{ protocol.signMode }}</td>
                  <td>{{ protocol.sceneScope }}</td>
                  <td>{{ protocol.channelScope }}</td>
                  <td>{{ protocol.merchantAckRequired }}</td>
                  <td class="flow-summary-cell">{{ protocol.riskControlTag }}</td>
                  <td><span :class="['badge', protocol.statusType]">{{ protocol.status }}</span></td>
                  <td>{{ protocol.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === protocol.protocolCode"
                      @click="toggleProtocol(protocol)"
                    >
                      {{ protocol.status === "ENABLED" ? "停用" : "启用" }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
