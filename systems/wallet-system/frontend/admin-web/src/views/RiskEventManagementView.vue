<script setup>
import { onMounted, ref } from "vue";
import { walletApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const form = ref({
  eventNo: "",
  action: "APPROVED",
  handledBy: "风控主管张敏",
  handledRemark: "人工复核通过"
});

async function load() {
  loading.value = true;
  message.value = "";
  try {
    rows.value = await walletApi.getRiskEvents();
    if (!form.value.eventNo && rows.value.length > 0) {
      form.value.eventNo = rows.value[0].eventNo;
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function approve() {
  message.value = "";
  try {
    await walletApi.approveRiskEvent(form.value);
    await load();
    message.value = "审批完成";
  } catch (error) {
    message.value = error.message;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel">
      <h2>风控审批</h2>
      <div v-if="message" style="margin-bottom:12px;color:#1d4ed8">{{ message }}</div>
      <div class="layout">
        <div class="table-wrap">
          <div v-if="loading">加载中...</div>
          <table v-else>
            <thead>
              <tr>
                <th>事件号</th>
                <th>业务类型</th>
                <th>业务单号</th>
                <th>风险等级</th>
                <th>状态</th>
                <th>原因</th>
                <th>处理人</th>
                <th>处理时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.eventNo">
                <td>{{ row.eventNo }}</td>
                <td>{{ row.bizType }}</td>
                <td>{{ row.bizNo }}</td>
                <td>{{ row.riskLevel }}</td>
                <td><span class="badge">{{ row.status }}</span></td>
                <td>{{ row.riskReason }}</td>
                <td>{{ row.handledBy }}</td>
                <td>{{ row.handledAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="detail-grid">
          <div class="detail-card">
            <div class="detail-label">审批操作</div>
            <div style="display:grid;gap:8px;margin-top:8px">
              <input v-model="form.eventNo" placeholder="事件号" />
              <select v-model="form.action">
                <option value="APPROVED">通过</option>
                <option value="REJECTED">驳回</option>
              </select>
              <input v-model="form.handledBy" placeholder="处理人" />
              <input v-model="form.handledRemark" placeholder="处理备注" />
              <button class="button" style="background:#7c3aed;color:#fff" @click="approve">提交审批</button>
            </div>
          </div>
          <div class="detail-card">
            <div class="detail-label">审批说明</div>
            <div style="margin-top:8px;display:grid;gap:6px;color:#334155">
              <div>1. 红包总金额达到阈值时自动生成待审事件</div>
              <div>2. 审批通过后自动完成红包出资和流水记账</div>
              <div>3. 审批驳回后红包批次保持驳回状态</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
