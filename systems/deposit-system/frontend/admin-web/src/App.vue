<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { getAccounts, getFlows, offsetDebt, openAccount, postAction } from "./api";

const accounts = ref([]);
const flows = ref([]);
const selected = ref(null);
const errorMessage = ref("");
const activeView = ref("overview");
const loading = ref(false);
const form = reactive({ ownerId: "", ownerType: "WORKER", requiredAmount: 0 });
const action = reactive({ type: "collect", amount: "", referenceNo: "", remark: "" });
const debt = reactive({ debtNo: "", debtAmount: "", remark: "" });
const actionLabels = {
  collect: "收取保证金", freeze: "冻结保证金", unfreeze: "解冻保证金",
  deduct: "扣罚保证金", refund: "退还保证金"
};
const totalBalance = computed(() => accounts.value.reduce((sum, item) => sum + Number(item.balance || 0), 0));
const totalFrozen = computed(() => accounts.value.reduce((sum, item) => sum + Number(item.frozenAmount || 0), 0));

const load = async () => {
  loading.value = true;
  errorMessage.value = "";
  try {
    accounts.value = await getAccounts();
    if (selected.value) {
      selected.value = accounts.value.find((item) => item.accountNo === selected.value.accountNo) || selected.value;
      flows.value = await getFlows(selected.value.accountNo);
    }
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
};
const selectAccount = async (account) => {
  selected.value = account;
  flows.value = await getFlows(account.accountNo);
  activeView.value = "detail";
};
const create = async () => {
  try {
    await openAccount(form);
    Object.assign(form, { ownerId: "", ownerType: "WORKER", requiredAmount: 0 });
    await load();
    activeView.value = "accounts";
  } catch (error) { errorMessage.value = error.message; }
};
const runAction = async () => {
  if (!selected.value) return;
  try {
    await postAction(selected.value.accountNo, action.type, {
      amount: Number(action.amount), referenceNo: action.referenceNo, remark: action.remark
    });
    Object.assign(action, { amount: "", referenceNo: "", remark: "" });
    await load();
  } catch (error) { errorMessage.value = error.message; }
};
const runOffset = async () => {
  if (!selected.value) return;
  try {
    await offsetDebt({ accountNo: selected.value.accountNo, debtNo: debt.debtNo,
      debtAmount: Number(debt.debtAmount), remark: debt.remark });
    Object.assign(debt, { debtNo: "", debtAmount: "", remark: "" });
    await load();
  } catch (error) { errorMessage.value = error.message; }
};
onMounted(load);
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="mark">D</span><div><strong>保证金中心</strong><small>DEPOSIT CONTROL</small></div></div>
      <button class="nav" :class="{ active: activeView === 'overview' }" @click="activeView = 'overview'">概览工作台 <small>Overview</small></button>
      <button class="nav" :class="{ active: activeView === 'accounts' }" @click="activeView = 'accounts'">保证金账户 <small>Accounts</small></button>
      <button class="nav" :class="{ active: activeView === 'detail' }" :disabled="!selected" @click="activeView = 'detail'">账户流水与作业 <small>Operations</small></button>
      <div class="note">保证金动作均写入不可变流水；真实资金渠道需由收款、退款和账务系统联调接入。</div>
    </aside>
    <main class="main">
      <header class="topbar"><div><p>HOME SERVICE PAYMENT PLATFORM / DEPOSIT DOMAIN</p><h1>{{ activeView === "overview" ? "保证金运营工作台" : activeView === "accounts" ? "保证金账户" : "账户作业详情" }}</h1></div><button class="ghost" @click="load">刷新</button></header>
      <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
      <section v-if="activeView === 'overview'">
        <div class="hero panel"><div><span>DEPOSIT LIFECYCLE</span><h2>让每一笔保证金都有余额、状态和责任链路</h2><p>覆盖收取、冻结、解冻、扣罚、退还和欠款抵扣，并通过流水支持财务追溯。</p></div><button class="primary" @click="activeView = 'accounts'">进入账户作业</button></div>
        <div class="metrics"><article><span>账户数</span><strong>{{ accounts.length }}</strong><small>独立保证金账户</small></article><article class="green"><span>保证金余额</span><strong>¥{{ totalBalance.toFixed(2) }}</strong><small>账户余额合计</small></article><article class="warm"><span>冻结金额</span><strong>¥{{ totalFrozen.toFixed(2) }}</strong><small>不可用余额</small></article></div>
        <div class="panel process"><span>CONTROL RULES</span><h3>保证金生命周期</h3><div><b>开户</b><i>→</i><b>收取</b><i>→</i><b>冻结 / 解冻</b><i>→</i><b>扣罚 / 抵扣</b><i>→</i><b>退还</b></div></div>
      </section>
      <section v-else-if="activeView === 'accounts'" class="panel">
        <div class="section-head"><div><span>DEPOSIT ACCOUNTS</span><h2>保证金账户</h2></div><button class="primary" @click="activeView = 'create'">新开账户</button></div>
        <table class="table"><thead><tr><th>账户号</th><th>主体</th><th>应缴金额</th><th>余额</th><th>冻结</th><th>可用</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in accounts" :key="item.accountNo"><td><button class="link" @click="selectAccount(item)">{{ item.accountNo }}</button></td><td>{{ item.ownerId }}<small>{{ item.ownerType }}</small></td><td>¥{{ Number(item.requiredAmount).toFixed(2) }}</td><td class="money">¥{{ Number(item.balance).toFixed(2) }}</td><td>¥{{ Number(item.frozenAmount).toFixed(2) }}</td><td>¥{{ Number(item.availableAmount).toFixed(2) }}</td><td><em>{{ item.status }}</em></td><td><button class="link" @click="selectAccount(item)">查看流水</button></td></tr></tbody></table>
        <div v-if="!accounts.length && !loading" class="empty">暂无保证金账户</div>
      </section>
      <section v-else-if="activeView === 'create'" class="panel form-panel"><span>OPEN ACCOUNT</span><h2>新开保证金账户</h2><label>主体编号<input v-model="form.ownerId" placeholder="WORKER-1001" /></label><label>主体类型<select v-model="form.ownerType"><option>WORKER</option><option>MERCHANT</option><option>PLATFORM</option></select></label><label>应缴金额<input v-model.number="form.requiredAmount" type="number" min="0" /></label><div class="actions"><button class="primary" @click="create">创建账户</button><button class="ghost" @click="activeView = 'accounts'">取消</button></div></section>
      <section v-else class="detail">
        <div class="panel"><div class="section-head"><div><span>ACCOUNT OPERATIONS</span><h2>{{ selected?.accountNo }}</h2></div><button class="ghost" @click="activeView = 'accounts'">返回列表</button></div><div class="summary"><div><small>主体</small><strong>{{ selected?.ownerId }}</strong></div><div><small>余额</small><strong class="money">¥{{ Number(selected?.balance || 0).toFixed(2) }}</strong></div><div><small>冻结</small><strong>¥{{ Number(selected?.frozenAmount || 0).toFixed(2) }}</strong></div><div><small>可用</small><strong>¥{{ Number(selected?.availableAmount || 0).toFixed(2) }}</strong></div></div><div class="operation-form"><select v-model="action.type"><option v-for="(label, type) in actionLabels" :key="type" :value="type">{{ label }}</option></select><input v-model="action.amount" type="number" min="0" placeholder="金额" /><input v-model="action.referenceNo" placeholder="业务关联号" /><input v-model="action.remark" placeholder="备注" /><button class="primary" @click="runAction">执行动作</button></div><div class="operation-form offset"><input v-model="debt.debtNo" placeholder="欠款单号" /><input v-model="debt.debtAmount" type="number" min="0" placeholder="抵扣金额" /><input v-model="debt.remark" placeholder="抵扣备注" /><button class="secondary" @click="runOffset">抵扣欠款</button></div></div>
        <div class="panel"><span>DEPOSIT FLOWS</span><h3>资金流水</h3><table class="table"><thead><tr><th>流水号</th><th>类型</th><th>发生金额</th><th>余额变化</th><th>冻结变化</th><th>关联号</th><th>时间</th></tr></thead><tbody><tr v-for="item in flows" :key="item.flowNo"><td>{{ item.flowNo }}</td><td><em>{{ item.flowType }}</em></td><td class="money">¥{{ Number(item.amount).toFixed(2) }}</td><td>¥{{ Number(item.beforeBalance).toFixed(2) }} → ¥{{ Number(item.afterBalance).toFixed(2) }}</td><td>¥{{ Number(item.beforeFrozenAmount || 0).toFixed(2) }} → ¥{{ Number(item.afterFrozenAmount || 0).toFixed(2) }}</td><td>{{ item.referenceNo || "-" }}</td><td>{{ item.createdAt?.replace("T", " ") }}</td></tr></tbody></table><div v-if="!flows.length" class="empty">暂无流水</div></div>
      </section>
    </main>
  </div>
</template>
