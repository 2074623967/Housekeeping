<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const cashier = ref(null);
const loading = ref(true);
const message = ref("");
const selected = ref("微信支付");

onMounted(async () => {
  try {
    cashier.value = await paymentApi.getCashier(route.params.prepayOrderNo);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
});

const channels = computed(() => cashier.value?.channels || []);

async function pay() {
  const next = await paymentApi.submit({
    prepayOrderNo: cashier.value.prepayOrderNo,
    paymentMethod: selected.value,
    channelCode: selected.value === "微信支付" ? "wx_jsapi" : selected.value === "支付宝" ? "alipay_h5" : "bank_card"
  });
  router.push({
    path: "/result/pending",
    query: {
      prepayOrderNo: next?.prepayOrderNo,
      paymentOrderId: next?.paymentOrderId,
      paymentMethod: selected.value
    }
  });
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
            :class="['button', selected === item ? 'primary' : 'secondary']"
            @click="selected = item"
          >
            {{ item }}
          </button>
        </div>
        <button class="button primary" @click="pay">确认支付</button>
      </div>
      <div class="panel">
        <h3 class="title">订单信息</h3>
        <div class="row"><span>客户</span><span>{{ cashier.customerName }}</span></div>
        <div class="row"><span>场景</span><span>{{ cashier.payScene }}</span></div>
        <div class="row"><span>状态</span><span>{{ cashier.status }}</span></div>
        <div class="row"><span>到期时间</span><span>{{ cashier.expiresAt }}</span></div>
      </div>
    </div>
  </div>
</template>
