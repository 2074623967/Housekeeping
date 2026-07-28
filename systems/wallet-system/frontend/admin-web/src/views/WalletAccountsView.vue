<script setup>
import { onMounted, ref } from "vue";
import { walletApi } from "../api/client";

const rows = ref([]);
const detail = ref(null);
const loading = ref(false);
const message = ref("");
const rechargeForm = ref({
  accountNo: "",
  bizNo: "",
  amount: "100.00",
  operatorName: "运营小王"
});
const withdrawForm = ref({
  accountNo: "",
  bizNo: "",
  amount: "50.00",
  operatorName: "运营小王"
});
const transferForm = ref({
  sourceAccountNo: "",
  targetAccountNo: "",
  bizNo: "",
  amount: "30.00",
  operatorName: "运营小王"
});

async function load() {
  loading.value = true;
  message.value = "";
  rows.value = await walletApi.getAccounts();
  detail.value = rows.value[0] ? await walletApi.getDetail(rows.value[0].accountNo) : null;
  if (!rechargeForm.value.accountNo && rows.value[0]) {
    rechargeForm.value.accountNo = rows.value[0].accountNo;
  }
  if (!withdrawForm.value.accountNo && rows.value[0]) {
    withdrawForm.value.accountNo = rows.value[0].accountNo;
  }
  if (!transferForm.value.sourceAccountNo && rows.value[0]) {
    transferForm.value.sourceAccountNo = rows.value[0].accountNo;
  }
  if (!transferForm.value.targetAccountNo && rows.value[1]) {
    transferForm.value.targetAccountNo = rows.value[1].accountNo;
  }
  loading.value = false;
}

async function openDetail(accountNo) {
  detail.value = await walletApi.getDetail(accountNo);
}

async function recharge() {
  message.value = "";
  try {
    await walletApi.recharge({
      ...rechargeForm.value,
      amount: Number(rechargeForm.value.amount)
    });
    await load();
    message.value = "充值成功";
  } catch (error) {
    message.value = error.message;
  }
}

async function withdraw() {
  message.value = "";
  try {
    await walletApi.withdraw({
      ...withdrawForm.value,
      amount: Number(withdrawForm.value.amount)
    });
    await load();
    message.value = "提现成功";
  } catch (error) {
    message.value = error.message;
  }
}

async function transfer() {
  message.value = "";
  try {
    await walletApi.transfer({
      ...transferForm.value,
      amount: Number(transferForm.value.amount)
    });
    await load();
    message.value = "转账成功";
  } catch (error) {
    message.value = error.message;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel">
      <h2>钱包账户</h2>
      <div v-if="message" style="margin-bottom:12px;color:#1d4ed8">{{ message }}</div>
      <div v-if="loading">加载中...</div>
      <div v-else class="layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>账户号</th><th>用户</th><th>类型</th><th>状态</th><th>可用</th><th>冻结</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.accountNo" @click="openDetail(row.accountNo)" style="cursor:pointer">
                <td>{{ row.accountNo }}</td>
                <td>{{ row.ownerName }}</td>
                <td>{{ row.walletType }}</td>
                <td><span class="badge">{{ row.status }}</span></td>
                <td>{{ row.availableAmount }}</td>
                <td>{{ row.frozenAmount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="detail-grid" v-if="detail">
          <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ detail.account.accountNo }}</div></div>
          <div class="detail-card"><div class="detail-label">用户</div><div class="detail-value">{{ detail.account.ownerName }}</div></div>
          <div class="detail-card"><div class="detail-label">可用余额</div><div class="detail-value">{{ detail.account.availableAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">最近流水数</div><div class="detail-value">{{ detail.ledgers.length }}</div></div>
          <div class="detail-card" style="grid-column:1/-1">
            <div class="detail-label">充值单</div>
            <div style="display:grid;gap:8px">
              <input v-model="rechargeForm.accountNo" placeholder="账户号" />
              <input v-model="rechargeForm.bizNo" placeholder="业务单号" />
              <input v-model="rechargeForm.amount" placeholder="充值金额" />
              <input v-model="rechargeForm.operatorName" placeholder="操作人" />
              <button class="button" style="background:#2563eb;color:#fff" @click="recharge">发起充值</button>
            </div>
          </div>
          <div class="detail-card" style="grid-column:1/-1">
            <div class="detail-label">提现单</div>
            <div style="display:grid;gap:8px">
              <input v-model="withdrawForm.accountNo" placeholder="账户号" />
              <input v-model="withdrawForm.bizNo" placeholder="业务单号" />
              <input v-model="withdrawForm.amount" placeholder="提现金额" />
              <input v-model="withdrawForm.operatorName" placeholder="操作人" />
              <button class="button" style="background:#0f766e;color:#fff" @click="withdraw">发起提现</button>
            </div>
          </div>
          <div class="detail-card" style="grid-column:1/-1">
            <div class="detail-label">转账单</div>
            <div style="display:grid;gap:8px">
              <input v-model="transferForm.sourceAccountNo" placeholder="转出账户号" />
              <input v-model="transferForm.targetAccountNo" placeholder="转入账户号" />
              <input v-model="transferForm.bizNo" placeholder="业务单号" />
              <input v-model="transferForm.amount" placeholder="转账金额" />
              <input v-model="transferForm.operatorName" placeholder="操作人" />
              <button class="button" style="background:#7c3aed;color:#fff" @click="transfer">发起转账</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
