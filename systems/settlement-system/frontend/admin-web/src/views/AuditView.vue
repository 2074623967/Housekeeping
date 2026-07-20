<script setup>
import { ref } from "vue";
import { orderApi } from "../api/client";

const settlementNo = ref("SLT20001");
const operatorName = ref("财务主管");
const auditRemark = ref("审核通过");
const message = ref("");

async function approve() {
  await orderApi.audit(settlementNo.value, { operatorName: operatorName.value, auditRemark: auditRemark.value });
  message.value = "审核已提交";
}

async function reject() {
  await orderApi.reject(settlementNo.value, { operatorName: operatorName.value, auditRemark: auditRemark.value });
  message.value = "驳回已提交";
}
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>审核页</h2>
        <p>对结算单进行通过或退回</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>结算单号</label><input v-model="settlementNo" /></div>
        <div class="field"><label>审核人</label><input v-model="operatorName" /></div>
        <div class="field"><label>备注</label><input v-model="auditRemark" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="approve">通过</button><button class="button secondary" @click="reject">退回</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
    </section>
  </div>
</template>
