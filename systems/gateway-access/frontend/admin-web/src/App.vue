<script setup>
import { onMounted, ref } from "vue";
import { gatewayAccessApi } from "./api/client";

const summary = ref({ metrics: [], highlights: [] });
const applications = ref([]);
const gateways = ref([]);
const certificates = ref([]);
const permissions = ref([]);
const loading = ref(true);
const message = ref("");
const active = ref("");

async function loadAll() {
  loading.value = true;
  message.value = "";
  try {
    const [summaryData, appData, gatewayData, certData, permissionData] = await Promise.all([
      gatewayAccessApi.getSummary(),
      gatewayAccessApi.getApplications(),
      gatewayAccessApi.getGateways(),
      gatewayAccessApi.getCertificates(),
      gatewayAccessApi.getPermissions()
    ]);
    summary.value = summaryData;
    applications.value = appData.records;
    gateways.value = gatewayData.records;
    certificates.value = certData.records;
    permissions.value = permissionData.records;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function toggle(run, code, enabled, label) {
  active.value = code;
  try {
    const summaryData = await run(code, enabled);
    summary.value = summaryData;
    const appData = await gatewayAccessApi.getApplications();
    const gatewayData = await gatewayAccessApi.getGateways();
    const certData = await gatewayAccessApi.getCertificates();
    const permissionData = await gatewayAccessApi.getPermissions();
    applications.value = appData.records;
    gateways.value = gatewayData.records;
    certificates.value = certData.records;
    permissions.value = permissionData.records;
    message.value = `${label} ${code} 已${enabled ? "启用" : "停用"}`;
  } catch (error) {
    message.value = `${label} ${code} 操作失败：${error.message}`;
  } finally {
    active.value = "";
  }
}

onMounted(loadAll);
</script>

<template>
  <div class="page">
    <header class="hero">
      <div>
        <p class="eyebrow">gateway-access</p>
        <h1>支付网关接入管理台</h1>
        <p class="lead">管理接入应用、渠道网关、证书和权限，作为 payment-core 的真实接入层。</p>
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
        <h2>系统说明</h2>
        <ul>
          <li v-for="item in summary.highlights" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="card">
        <div class="section-head">
          <h2>接入应用</h2>
        </div>
        <table>
          <thead>
            <tr>
              <th>应用编码</th>
              <th>应用名称</th>
              <th>来源系统</th>
              <th>负责人</th>
              <th>IP 白名单</th>
              <th>权限范围</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in applications" :key="item.appCode">
              <td>{{ item.appCode }}</td>
              <td>{{ item.appName }}</td>
              <td>{{ item.sourceSystem }}</td>
              <td>{{ item.owner }}</td>
              <td>{{ item.ipWhitelist }}</td>
              <td>{{ item.permissionScope }}</td>
              <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
              <td>{{ item.updatedAt }}</td>
              <td>
                <button class="link" :disabled="active === item.appCode" @click="toggle(gatewayAccessApi.toggleApplication, item.appCode, item.status !== 'ENABLED', '应用')">
                  {{ item.status === "ENABLED" ? "停用" : "启用" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card">
        <div class="section-head">
          <h2>渠道网关</h2>
        </div>
        <table>
          <thead>
            <tr>
              <th>网关编码</th>
              <th>名称</th>
              <th>渠道类型</th>
              <th>协议</th>
              <th>签名算法</th>
              <th>接入地址</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in gateways" :key="item.gatewayCode">
              <td>{{ item.gatewayCode }}</td>
              <td>{{ item.gatewayName }}</td>
              <td>{{ item.channelType }}</td>
              <td>{{ item.protocolType }}</td>
              <td>{{ item.signAlgorithm }}</td>
              <td>{{ item.endpoint }}</td>
              <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
              <td>{{ item.updatedAt }}</td>
              <td>
                <button class="link" :disabled="active === item.gatewayCode" @click="toggle(gatewayAccessApi.toggleGateway, item.gatewayCode, item.status !== 'ENABLED', '网关')">
                  {{ item.status === "ENABLED" ? "停用" : "启用" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card grid">
        <div>
          <div class="section-head"><h2>证书管理</h2></div>
          <table>
            <thead>
              <tr>
                <th>证书编码</th>
                <th>网关</th>
                <th>版本</th>
                <th>到期日</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in certificates" :key="item.certificateCode">
                <td>{{ item.certificateCode }}</td>
                <td>{{ item.gatewayCode }}</td>
                <td>{{ item.certificateVersion }}</td>
                <td>{{ item.expireAt }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td>{{ item.updatedAt }}</td>
                <td>
                  <button class="link" :disabled="active === item.certificateCode" @click="toggle(gatewayAccessApi.toggleCertificate, item.certificateCode, item.status !== 'ENABLED', '证书')">
                    {{ item.status === "ENABLED" ? "停用" : "启用" }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div>
          <div class="section-head"><h2>接入权限</h2></div>
          <table>
            <thead>
              <tr>
                <th>权限编码</th>
                <th>应用</th>
                <th>权限范围</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in permissions" :key="item.permissionCode">
                <td>{{ item.permissionCode }}</td>
                <td>{{ item.appCode }}</td>
                <td>{{ item.scope }}</td>
                <td><span class="tag" :class="item.statusType">{{ item.status }}</span></td>
                <td>{{ item.updatedAt }}</td>
                <td>
                  <button class="link" :disabled="active === item.permissionCode" @click="toggle(gatewayAccessApi.togglePermission, item.permissionCode, item.status !== 'ENABLED', '权限')">
                    {{ item.status === "ENABLED" ? "停用" : "启用" }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>
