<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { resolvePaymentChannelCode } from "../constants/payment";

const props = defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});

const TERMINAL_META = {
  app: {
    heroLabel: "家政服务 App 收银台",
    heroHint: "适用于家政用户在 App 内完成订单支付、补款和状态确认。",
    terminalCode: "APP_WEB",
    actionLabel: "确认支付",
    refreshLabel: "刷新收银台",
    closeLabel: "关闭本次支付"
  },
  h5: {
    heroLabel: "家政服务 H5 收银台",
    heroHint: "适用于活动落地页、短信唤起和服务预约页中的轻量支付场景。",
    terminalCode: "H5_WEB",
    actionLabel: "立即支付",
    refreshLabel: "刷新会话",
    closeLabel: "结束当前会话"
  },
  pc: {
    heroLabel: "家政服务 PC 收银台",
    heroHint: "适用于官网、运营后台代客支付和桌面浏览器场景，强调桌面端信息确认、扫码支付和失败补救动作。",
    terminalCode: "PC_WEB",
    actionLabel: "确认并发起支付",
    refreshLabel: "刷新桌面收银台",
    closeLabel: "关闭当前支付单"
  }
};

const CHANNEL_META = {
  微信支付: {
    icon: "WX",
    description: "适合大多数个人用户，回调速度快，适配 H5 / App。",
    settlementHint: "预计实时回调，异常时支持主动查单。 "
  },
  支付宝: {
    icon: "ALI",
    description: "适合 H5、生活服务与活动支付场景，兼容性稳定。",
    settlementHint: "适用于兜底路由和多终端支付结果收口。"
  },
  银行卡: {
    icon: "BANK",
    description: "适合企业客户、大额支付和线下转账认领。",
    settlementHint: "大额订单建议保留付款凭证，便于运营复核。"
  }
};

const route = useRoute();
const router = useRouter();
const cashier = ref(null);
const loading = ref(true);
const message = ref("");
const selectedPaymentMethod = ref("微信支付");
const submitLoading = ref(false);
const refreshLoading = ref(false);
const closeLoading = ref(false);
const idempotencyKey = ref("");
const countdownSeconds = ref(0);

let countdownTimer = null;

function generateIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `IDEMP-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startCountdown(expiresAt) {
  stopCountdown();
  if (!expiresAt) {
    countdownSeconds.value = 0;
    return;
  }
  const expireTimestamp = new Date(expiresAt.replace(" ", "T")).getTime();
  const updateCountdown = () => {
    const remainSeconds = Math.max(Math.floor((expireTimestamp - Date.now()) / 1000), 0);
    countdownSeconds.value = Number.isFinite(remainSeconds) ? remainSeconds : 0;
    if (remainSeconds <= 0) {
      stopCountdown();
    }
  };
  updateCountdown();
  countdownTimer = setInterval(updateCountdown, 1000);
}

async function loadCashier() {
  loading.value = true;
  message.value = "";
  try {
    cashier.value = await paymentApi.getCashier(route.params.prepayOrderNo);
    idempotencyKey.value = generateIdempotencyKey();
    if (cashier.value?.channels?.length > 0) {
      selectedPaymentMethod.value = cashier.value.channels[0];
    }
    startCountdown(cashier.value?.expiresAt);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadCashier);
onBeforeUnmount(stopCountdown);

const terminalMeta = computed(() => TERMINAL_META[props.terminalVariant] || TERMINAL_META.app);
const isPcVariant = computed(() => props.terminalVariant === "pc");
const channels = computed(() => cashier.value?.channels || []);
const hasChannels = computed(() => channels.value.length > 0);
const selectedChannelMeta = computed(() => CHANNEL_META[selectedPaymentMethod.value] || null);
const statusClass = computed(() => {
  const statusType = cashier.value?.statusType;
  return statusType ? `status-${statusType}` : "status-info";
});
const countdownLabel = computed(() => {
  const hours = String(Math.floor(countdownSeconds.value / 3600)).padStart(2, "0");
  const minutes = String(Math.floor((countdownSeconds.value % 3600) / 60)).padStart(2, "0");
  const seconds = String(countdownSeconds.value % 60).padStart(2, "0");
  return `${hours}:${minutes}:${seconds}`;
});
const canClose = computed(() => Boolean(cashier.value?.paymentOrderId) && countdownSeconds.value > 0);

async function refreshCashier() {
  refreshLoading.value = true;
  try {
    await loadCashier();
  } finally {
    refreshLoading.value = false;
  }
}

async function pay() {
  if (!cashier.value || !hasChannels.value) {
    message.value = "当前暂无可用支付方式，请稍后重试。";
    return;
  }
  submitLoading.value = true;
  message.value = "";
  try {
    const submitResult = await paymentApi.submit({
      prepayOrderNo: cashier.value.prepayOrderNo,
      paymentMethod: selectedPaymentMethod.value,
      channelCode: resolvePaymentChannelCode(selectedPaymentMethod.value),
      terminal: terminalMeta.value.terminalCode,
      idempotencyKey: idempotencyKey.value
    });
    router.push({
      path: `/payment-result/${submitResult?.paymentOrderId}`,
      query: {
        prepayOrderNo: submitResult?.prepayOrderNo,
        paymentMethod: selectedPaymentMethod.value,
        terminalVariant: props.terminalVariant
      }
    });
  } catch (error) {
    message.value = error.message;
  } finally {
    submitLoading.value = false;
  }
}

async function closeCurrentPayment() {
  if (!cashier.value?.paymentOrderId) {
    return;
  }
  closeLoading.value = true;
  message.value = "";
  try {
    await paymentApi.close({ paymentOrderId: cashier.value.paymentOrderId });
    message.value = "当前支付单已关闭，可回到订单中心重新发起支付。";
    await loadCashier();
  } catch (error) {
    message.value = error.message;
  } finally {
    closeLoading.value = false;
  }
}
</script>

<template>
  <div class="terminal-page" :class="`terminal-${terminalVariant}`">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">{{ terminalMeta.heroLabel }}</div>
        <h1>{{ cashier?.title || "支付收银台" }}</h1>
        <p>{{ terminalMeta.heroHint }}</p>
      </div>
      <div class="hero-amount-card">
        <div class="mini-label">本次应付</div>
        <div class="hero-amount">{{ cashier?.amount || "¥0.00" }}</div>
        <div class="hero-status-row">
          <span class="status-pill" :class="statusClass">{{ cashier?.status || "待支付" }}</span>
          <span class="countdown-pill">剩余 {{ countdownLabel }}</span>
        </div>
      </div>
    </section>

    <div v-if="loading" class="terminal-card terminal-state-card">收银台加载中...</div>
    <div v-else-if="message && !cashier" class="terminal-card terminal-state-card">{{ message }}</div>

    <div v-else class="terminal-grid">
      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>选择支付方式</h3>
            <p>系统会基于支付配置、支付场景和金额自动执行路由，以下为本次可用渠道。</p>
          </div>
          <span class="support-tag">多终端联调就绪</span>
        </div>

        <div v-if="!hasChannels" class="inline-warning">当前暂无可用支付方式，请联系运营检查支付渠道配置。</div>

        <div v-else class="channel-list">
          <button
            v-for="item in channels"
            :key="item"
            type="button"
            class="channel-card"
            :class="{ active: selectedPaymentMethod === item }"
            @click="selectedPaymentMethod = item"
          >
            <div class="channel-icon">{{ CHANNEL_META[item]?.icon || "PAY" }}</div>
            <div class="channel-content">
              <strong>{{ item }}</strong>
              <span>{{ CHANNEL_META[item]?.description || "系统已接入该支付方式。" }}</span>
            </div>
            <div class="channel-check">{{ selectedPaymentMethod === item ? "已选中" : "可使用" }}</div>
          </button>
        </div>

        <div v-if="selectedChannelMeta" class="channel-tips">
          <div>
            <strong>渠道说明</strong>
            <p>{{ selectedChannelMeta.description }}</p>
          </div>
          <div>
            <strong>结算提示</strong>
            <p>{{ selectedChannelMeta.settlementHint }}</p>
          </div>
        </div>

        <div class="terminal-actions">
          <button class="action-button primary" :disabled="submitLoading || !hasChannels" @click="pay">
            {{ submitLoading ? "支付提交流转中..." : terminalMeta.actionLabel }}
          </button>
          <button class="action-button secondary" :disabled="refreshLoading" @click="refreshCashier">
            {{ refreshLoading ? "刷新中..." : terminalMeta.refreshLabel }}
          </button>
          <button class="action-button ghost" :disabled="closeLoading || !canClose" @click="closeCurrentPayment">
            {{ closeLoading ? "关闭中..." : terminalMeta.closeLabel }}
          </button>
        </div>

        <p v-if="message" class="feedback-text">{{ message }}</p>

        <div v-if="isPcVariant" class="desktop-hint-grid">
          <div class="desktop-hint-card">
            <strong>桌面端支付建议</strong>
            <p>若用户使用微信或支付宝，建议展示二维码并保留当前页面，等待回调或主动查单完成收口。</p>
          </div>
          <div class="desktop-hint-card">
            <strong>失败补救动作</strong>
            <p>如浏览器关闭、扫码超时或渠道报错，可先关闭支付单，再由订单中心重新拉起新的预付单。</p>
          </div>
        </div>
      </section>

      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>订单与支付信息</h3>
            <p>确认本次支付对象、会话状态和后续联调所需的关键编号。</p>
          </div>
        </div>

        <div class="summary-grid">
          <div class="summary-item">
            <span>订单号</span>
            <strong>{{ cashier?.orderNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>账单号</span>
            <strong>{{ cashier?.billNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>预付单号</span>
            <strong>{{ cashier?.prepayOrderNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>支付单号</span>
            <strong>{{ cashier?.paymentOrderId || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>客户名称</span>
            <strong>{{ cashier?.customerName || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>支付场景</span>
            <strong>{{ cashier?.payScene || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>会话状态</span>
            <strong>{{ cashier?.status || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>到期时间</span>
            <strong>{{ cashier?.expiresAt || "-" }}</strong>
          </div>
        </div>

        <div class="ops-card">
          <div class="ops-title">联调留痕</div>
          <div class="ops-row"><span>终端标识</span><span>{{ terminalMeta.terminalCode }}</span></div>
          <div class="ops-row"><span>幂等键</span><span class="mono-text">{{ idempotencyKey || "-" }}</span></div>
          <div class="ops-row"><span>可选渠道</span><span>{{ channels.join(" / ") || "-" }}</span></div>
        </div>
      </section>
    </div>
  </div>
</template>
