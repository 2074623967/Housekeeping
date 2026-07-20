<script setup>
import { onMounted, ref } from "vue";
import { paymentConfigApi } from "../api/client";

const channels = ref([]);
const routeRules = ref([]);
const protocols = ref([]);
const returnCodeMappings = ref([]);
const gateways = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeConfigCode = ref("");
const editingProtocolCode = ref("");
const protocolForm = ref(createProtocolForm());

function createProtocolForm() {
  return {
    protocolCode: "",
    protocolName: "",
    protocolType: "",
    templateVersion: "",
    signMode: "",
    sceneScope: "",
    channelScope: "",
    merchantAckRequired: "需要",
    riskControlTag: "",
    priority: 99,
    enabled: true
  };
}

async function loadOverview() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const overview = await paymentConfigApi.getOverview();
    channels.value = overview.channels;
    routeRules.value = overview.routeRules;
    protocols.value = overview.protocols;
    returnCodeMappings.value = overview.returnCodeMappings;
    gateways.value = overview.gateways;
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

async function toggleReturnCodeMapping(mapping) {
  await toggleMappingConfig(
    mapping.channelCode,
    mapping.channelReturnCode,
    mapping.status !== "ENABLED",
    "返回码映射",
    paymentConfigApi.toggleReturnCodeMapping
  );
}

async function toggleGateway(gateway) {
  await toggleConfig(
    gateway.gatewayCode,
    gateway.status !== "ENABLED",
    "支付网关",
    paymentConfigApi.toggleGateway
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
    returnCodeMappings.value = overview.returnCodeMappings;
    gateways.value = overview.gateways;
    actionMessage.value = `${configType} ${configCode} 已${enabled ? "启用" : "停用"}。`;
  } catch (error) {
    actionMessage.value = `${configType} ${configCode} 操作失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function toggleMappingConfig(configCode, subCode, enabled, configType, toggleRunner) {
  activeConfigCode.value = `${configCode}:${subCode}`;
  actionMessage.value = "";
  try {
    const overview = await toggleRunner(configCode, subCode, enabled);
    channels.value = overview.channels;
    routeRules.value = overview.routeRules;
    protocols.value = overview.protocols;
    returnCodeMappings.value = overview.returnCodeMappings;
    gateways.value = overview.gateways;
    actionMessage.value = `${configType} ${configCode}/${subCode} 已${enabled ? "启用" : "停用"}。`;
  } catch (error) {
    actionMessage.value = `${configType} ${configCode}/${subCode} 操作失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateProtocol() {
  editingProtocolCode.value = "";
  protocolForm.value = createProtocolForm();
  actionMessage.value = "";
}

function startEditProtocol(protocol) {
  editingProtocolCode.value = protocol.protocolCode;
  protocolForm.value = {
    protocolCode: protocol.protocolCode,
    protocolName: protocol.protocolName,
    protocolType: protocol.protocolType,
    templateVersion: protocol.templateVersion,
    signMode: protocol.signMode,
    sceneScope: protocol.sceneScope,
    channelScope: protocol.channelScope,
    merchantAckRequired: protocol.merchantAckRequired,
    riskControlTag: protocol.riskControlTag,
    priority: protocol.priority,
    enabled: protocol.status === "ENABLED"
  };
  actionMessage.value = `正在编辑协议 ${protocol.protocolCode}`;
}

async function submitProtocolForm() {
  activeConfigCode.value = editingProtocolCode.value || protocolForm.value.protocolCode;
  actionMessage.value = "";
  try {
    const payload = {
      ...protocolForm.value,
      priority: Number(protocolForm.value.priority || 99)
    };
    const overview = editingProtocolCode.value
      ? await paymentConfigApi.updateProtocol(editingProtocolCode.value, payload)
      : await paymentConfigApi.createProtocol(payload);
    channels.value = overview.channels;
    routeRules.value = overview.routeRules;
    protocols.value = overview.protocols;
    returnCodeMappings.value = overview.returnCodeMappings;
    gateways.value = overview.gateways;
    actionMessage.value = editingProtocolCode.value
      ? `支付协议 ${editingProtocolCode.value} 已更新。`
      : `支付协议 ${protocolForm.value.protocolCode} 已新增。`;
    startCreateProtocol();
  } catch (error) {
    actionMessage.value = `支付协议保存失败：${error.message}`;
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
        <h2>支付配置中心</h2>
        <p>统一管理支付渠道、路由规则、支付协议、返回码映射和支付网关接入配置</p>
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
            <button class="button secondary" @click="startCreateProtocol">新建协议</button>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ editingProtocolCode ? "编辑支付协议" : "新增支付协议" }}</h3>
                <p class="meta">按协议模板、渠道范围、商户确认和风控标签维护支付协议标准配置</p>
              </div>
            </div>

            <div class="filter-grid">
              <label>
                协议编码
                <input v-model.trim="protocolForm.protocolCode" :disabled="Boolean(editingProtocolCode)" />
              </label>
              <label>
                协议名称
                <input v-model.trim="protocolForm.protocolName" />
              </label>
              <label>
                协议类型
                <input v-model.trim="protocolForm.protocolType" placeholder="PAYMENT_SIGN / PREAUTH / WITHHOLD" />
              </label>
              <label>
                模板版本
                <input v-model.trim="protocolForm.templateVersion" placeholder="v1.0.0" />
              </label>
              <label>
                签约模式
                <input v-model.trim="protocolForm.signMode" />
              </label>
              <label>
                商户确认
                <select v-model="protocolForm.merchantAckRequired">
                  <option value="需要">需要</option>
                  <option value="不需要">不需要</option>
                </select>
              </label>
              <label>
                适用场景
                <input v-model.trim="protocolForm.sceneScope" placeholder="保洁/月嫂/到家服务" />
              </label>
              <label>
                适用渠道
                <input v-model.trim="protocolForm.channelScope" placeholder="wx_h5,alipay_h5" />
              </label>
              <label>
                风控标签
                <input v-model.trim="protocolForm.riskControlTag" placeholder="实名+重复签约校验" />
              </label>
              <label>
                优先级
                <input v-model.number="protocolForm.priority" type="number" min="0" />
              </label>
              <label>
                启用状态
                <select v-model="protocolForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
            </div>

            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button
                class="button primary"
                :disabled="activeConfigCode === (editingProtocolCode || protocolForm.protocolCode)"
                @click="submitProtocolForm"
              >
                {{ editingProtocolCode ? "保存修改" : "新增协议" }}
              </button>
              <button class="button secondary" @click="startCreateProtocol">重置表单</button>
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
                  <th>优先级</th>
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
                  <td>{{ protocol.priority }}</td>
                  <td><span :class="['badge', protocol.statusType]">{{ protocol.status }}</span></td>
                  <td>{{ protocol.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === protocol.protocolCode"
                      @click="startEditProtocol(protocol)"
                    >
                      编辑
                    </button>
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

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>渠道返回码映射</h3>
              <p class="meta">统一微信、支付宝、银行通道的错误码口径，沉淀标准化错误文案与处理建议</p>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>渠道编码</th>
                  <th>渠道返回码</th>
                  <th>标准错误码</th>
                  <th>标准错误文案</th>
                  <th>处理建议</th>
                  <th>可重试</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="mapping in returnCodeMappings" :key="`${mapping.channelCode}-${mapping.channelReturnCode}`">
                  <td>{{ mapping.channelCode }}</td>
                  <td>{{ mapping.channelReturnCode }}</td>
                  <td>{{ mapping.standardizedCode }}</td>
                  <td>{{ mapping.standardizedMessage }}</td>
                  <td class="flow-summary-cell">{{ mapping.handlingSuggestion }}</td>
                  <td>{{ mapping.retryable }}</td>
                  <td><span :class="['badge', mapping.statusType]">{{ mapping.status }}</span></td>
                  <td>{{ mapping.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === `${mapping.channelCode}:${mapping.channelReturnCode}`"
                      @click="toggleReturnCodeMapping(mapping)"
                    >
                      {{ mapping.status === "ENABLED" ? "停用" : "启用" }}
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
              <h3>支付网关接入管理</h3>
              <p class="meta">统一维护渠道接入模式、协议算法、超时重试和网关基础地址，支撑真实渠道适配器正式化</p>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>网关编码</th>
                  <th>网关名称</th>
                  <th>接入模式</th>
                  <th>适用渠道</th>
                  <th>基础地址</th>
                  <th>报文协议</th>
                  <th>签名算法</th>
                  <th>超时时间</th>
                  <th>重试策略</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="gateway in gateways" :key="gateway.gatewayCode">
                  <td>{{ gateway.gatewayCode }}</td>
                  <td>{{ gateway.gatewayName }}</td>
                  <td>{{ gateway.accessMode }}</td>
                  <td>{{ gateway.channelScope }}</td>
                  <td class="flow-summary-cell">{{ gateway.apiBaseUrl }}</td>
                  <td>{{ gateway.protocolType }}</td>
                  <td>{{ gateway.signAlgorithm }}</td>
                  <td>{{ gateway.timeoutMs }}</td>
                  <td class="flow-summary-cell">{{ gateway.retryPolicy }}</td>
                  <td><span :class="['badge', gateway.statusType]">{{ gateway.status }}</span></td>
                  <td>{{ gateway.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === gateway.gatewayCode"
                      @click="toggleGateway(gateway)"
                    >
                      {{ gateway.status === "ENABLED" ? "停用" : "启用" }}
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
