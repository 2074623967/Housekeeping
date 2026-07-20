<script setup>
import { ref } from "vue";
import { orderApi } from "../api/client";

const settlementNo = ref("SLT20001");
const detail = ref(null);
const message = ref("");

async function loadDetail() {
  message.value = "";
  try {
    detail.value = await orderApi.getDetail(settlementNo.value);
  } catch (error) {
    message.value = error.message;
  }
}
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算详情</h2>
        <p>展示结算明细项和审核链路</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>结算单号</label><input v-model="settlementNo" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadDetail">查询详情</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="detail" class="state-box">
        <p>{{ detail.order.settlementNo }} · {{ detail.order.targetName }} · {{ detail.order.netSettleAmount }}</p>
      </div>
      <div v-else class="state-box">请输入结算单号后查询</div>
    </section>
  </div>
</template>
