<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { resolvePaymentChannelCode } from "../constants/payment";

const route = useRoute();
const router = useRouter();
const cashier = ref(null);
const loading = ref(true);
const message = ref("");
const selectedPaymentMethod = ref("微信支付");
const submitLoading = ref(false);
const refreshLoading = ref(false);
const idempotencyKey = ref("");

function generateIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `IDEMP-${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadCashier);

const channels = computed(() => cashier.value?.channels || []);
const hasChannels = computed(() => channels.value.length > 0);

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
      terminal: "APP_WEB",
      idempotencyKey: idempotencyKey.value
    });
    router.push({
      path: `/payment-result/${submitResult?.paymentOrderId}`,
      query: {
        prepayOrderNo: submitResult?.prepayOrderNo,
        paymentMethod: selectedPaymentMethod.value
      }
    });
  } catch (error) {
    message.value = error.message;
  } finally {
    submitLoading.value = false;
  }
}
</script>

<template>
  <div class="page">
    <div class="hero">
      <div class="muted">家政服务收银台</div>
        <h1>{{ cashier?.title || "支付收银台" }}</h1>
        <div class="amount">{{ cashier?.amount || "¥0.00" }}</div>
        <div class="chips">
          <span class="chip">订单号 {{ cashier?.orderNo }}</span>
          <span class="chip">账单号 {{ cashier?.billNo }}</span>
          <span class="chip">预付单号 {{ cashier?.prepayOrderNo }}</span>
        </div>
        <p class="muted">请在有效期内完成支付，超时后系统会自动关闭当前会话。</p>
      </div>

      <div v-if="loading" class="panel">收银台加载中...</div>
      <div v-else-if="message" class="panel">{{ message }}</div>

    <div v-else class="grid">
      <div class="panel">
        <h3 class="title">选择支付方式</h3>
        <div class="chips">
          <button
            v-for="item in channels"
            :key="item"
            :class="['button', selectedPaymentMethod === item ? 'primary' : 'secondary']"
            @click="selectedPaymentMethod = item"
          >
            {{ item }}
          </button>
        </div>
        <div v-if="!hasChannels" class="inline-message">当前暂无可用渠道，请稍后刷新页面。</div>
        <div class="action-row">
          <button class="button primary" :disabled="submitLoading || !hasChannels" @click="pay">
            {{ submitLoading ? "提交中..." : "确认支付" }}
          </button>
          <button class="button secondary" :disabled="refreshLoading" @click="refreshCashier">
            {{ refreshLoading ? "刷新中..." : "刷新收银台" }}
          </button>
        </div>
        <p v-if="message" class="inline-message">{{ message }}</p>
      </div>
      <div class="panel">
        <h3 class="title">订单信息</h3>
        <div class="row"><span>客户</span><span>{{ cashier.customerName }}</span></div>
        <div class="row"><span>场景</span><span>{{ cashier.payScene }}</span></div>
        <div class="row"><span>状态</span><span>{{ cashier.status }}</span></div>
        <div class="row"><span>到期时间</span><span>{{ cashier.expiresAt }}</span></div>
        <div class="row"><span>可选渠道</span><span>{{ channels.join(" / ") || "-" }}</span></div>
        <div class="row"><span>幂等键</span><span>{{ idempotencyKey || "-" }}</span></div>
      </div>
    </div>
  </div>
</template>
