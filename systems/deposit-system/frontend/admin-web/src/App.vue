<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import {
  buildAccountsExportUrl,
  buildFlowsExportUrl,
  getAccountDetail,
  getAccountsByQuery,
  getFlows,
  getOverview,
  getV2Migrations,
  getV2Overview,
  getV2TotalAccountDetail,
  getV2TotalAccounts,
  offsetDebt,
  openAccount,
  postAction
} from "./api";

const overview = ref({
  accountCount: 0,
  ownerCount: 0,
  totalBalance: 0,
  totalFrozenAmount: 0,
  totalAvailableAmount: 0,
  fullyPaidAccountCount: 0
});
const accounts = ref([]);
const selected = ref(null);
const selectedDetail = ref(null);
const flows = ref([]);
const v2Overview = ref({
  totalAccountCount: 0,
  bindingCount: 0,
  migrationCandidateCount: 0,
  totalRequiredAmount: 0,
  totalBalance: 0,
  totalFrozenAmount: 0,
  totalAvailableAmount: 0
});
const totalAccounts = ref([]);
const totalAccountDetail = ref(null);
const migrations = ref([]);
const selectedTotalAccountNo = ref("");
const loading = ref(false);
const errorMessage = ref("");
const activeView = ref("overview");

const accountFilter = reactive({ ownerType: "", status: "", keyword: "" });
const flowFilter = reactive({ flowType: "", referenceNo: "" });
const form = reactive({ ownerId: "", ownerType: "WORKER", requiredAmount: 0 });
const action = reactive({ type: "collect", amount: "", referenceNo: "", remark: "" });
const debt = reactive({ debtNo: "", debtAmount: "", remark: "" });

const actionLabels = {
  collect: "收取保证金",
  freeze: "冻结保证金",
  unfreeze: "解冻保证金",
  deduct: "扣罚保证金",
  refund: "退还保证金"
};

const totalBalance = computed(() => Number(overview.value.totalBalance || 0).toFixed(2));
const totalFrozen = computed(() => Number(overview.value.totalFrozenAmount || 0).toFixed(2));
const totalAvailable = computed(() => Number(overview.value.totalAvailableAmount || 0).toFixed(2));
const v2TotalBalance = computed(() => Number(v2Overview.value.totalBalance || 0).toFixed(2));
const v2TotalRequiredAmount = computed(() => Number(v2Overview.value.totalRequiredAmount || 0).toFixed(2));
const v2TotalFrozen = computed(() => Number(v2Overview.value.totalFrozenAmount || 0).toFixed(2));
const v2TotalAvailable = computed(() => Number(v2Overview.value.totalAvailableAmount || 0).toFixed(2));

const loadOverview = async () => {
  overview.value = await getOverview();
};

const loadAccounts = async () => {
  accounts.value = await getAccountsByQuery(accountFilter);
};

const loadSelected = async () => {
  if (!selected.value) {
    selectedDetail.value = null;
    flows.value = [];
    return;
  }
  selectedDetail.value = await getAccountDetail(selected.value.accountNo);
  flows.value = await getFlows(selected.value.accountNo, flowFilter);
};

const refresh = async () => {
  loading.value = true;
  errorMessage.value = "";
  try {
    await Promise.all([loadOverview(), loadAccounts(), loadSelected(), loadV2Preview()]);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
};

const loadV2Preview = async () => {
  v2Overview.value = await getV2Overview();
  totalAccounts.value = await getV2TotalAccounts();
  migrations.value = await getV2Migrations();
  if (!totalAccounts.value.length) {
    totalAccountDetail.value = null;
    selectedTotalAccountNo.value = "";
    return;
  }
  if (!selectedTotalAccountNo.value || !totalAccounts.value.some((item) => item.totalAccountNo === selectedTotalAccountNo.value)) {
    selectedTotalAccountNo.value = totalAccounts.value[0].totalAccountNo;
  }
  totalAccountDetail.value = await getV2TotalAccountDetail(selectedTotalAccountNo.value);
};

const searchAccounts = async () => {
  loading.value = true;
  errorMessage.value = "";
  try {
    await loadAccounts();
    activeView.value = "accounts";
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
};

const selectAccount = async (account) => {
  selected.value = account;
  activeView.value = "detail";
  await loadSelected();
};

const selectTotalAccount = async (item) => {
  selectedTotalAccountNo.value = item.totalAccountNo;
  totalAccountDetail.value = await getV2TotalAccountDetail(item.totalAccountNo);
  activeView.value = "v2";
};

const create = async () => {
  try {
    await openAccount(form);
    Object.assign(form, { ownerId: "", ownerType: "WORKER", requiredAmount: 0 });
    await refresh();
    activeView.value = "accounts";
  } catch (error) {
    errorMessage.value = error.message;
  }
};

const runAction = async () => {
  if (!selected.value) {
    return;
  }
  try {
    await postAction(selected.value.accountNo, action.type, {
      amount: Number(action.amount),
      referenceNo: action.referenceNo,
      remark: action.remark
    });
    Object.assign(action, { amount: "", referenceNo: "", remark: "" });
    await refresh();
  } catch (error) {
    errorMessage.value = error.message;
  }
};

const runOffset = async () => {
  if (!selected.value) {
    return;
  }
  try {
    await offsetDebt({
      accountNo: selected.value.accountNo,
      debtNo: debt.debtNo,
      debtAmount: Number(debt.debtAmount),
      remark: debt.remark
    });
    Object.assign(debt, { debtNo: "", debtAmount: "", remark: "" });
    await refresh();
  } catch (error) {
    errorMessage.value = error.message;
  }
};

const applyFlowFilter = async () => {
  if (!selected.value) {
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  try {
    flows.value = await getFlows(selected.value.accountNo, flowFilter);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
};

const exportAccounts = () => {
  window.open(buildAccountsExportUrl(accountFilter), "_blank", "noopener,noreferrer");
};

const exportFlows = () => {
  if (!selected.value) {
    return;
  }
  window.open(buildFlowsExportUrl(selected.value.accountNo, flowFilter), "_blank", "noopener,noreferrer");
};

watch(
  () => selected.value?.accountNo,
  async () => {
    await loadSelected();
  }
);

onMounted(refresh);
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="mark">D</span>
        <div>
          <strong>保证金中心</strong>
          <small>DEPOSIT CONTROL</small>
        </div>
      </div>
      <button class="nav" :class="{ active: activeView === 'overview' }" @click="activeView = 'overview'">
        概览工作台 <small>Overview</small>
      </button>
      <button class="nav" :class="{ active: activeView === 'accounts' }" @click="activeView = 'accounts'">
        保证金账户 <small>Accounts</small>
      </button>
      <button class="nav" :class="{ active: activeView === 'v2' }" @click="activeView = 'v2'">
        V2 演进 <small>Preview</small>
      </button>
      <button class="nav" :class="{ active: activeView === 'detail' }" :disabled="!selected" @click="activeView = 'detail'">
        账户详情 <small>Detail</small>
      </button>
      <div class="note">
        <b>保证金规范</b>
        <div>账户、流水、冻结、解冻、抵扣和退还都保留完整追踪。</div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <p>HOME SERVICE PAYMENT PLATFORM / DEPOSIT DOMAIN</p>
          <h1>{{ activeView === "overview" ? "保证金运营工作台" : activeView === "accounts" ? "保证金账户" : activeView === "v2" ? "V2 演进预览" : "账户详情" }}</h1>
        </div>
        <div class="top-actions">
          <button class="ghost" @click="refresh">刷新</button>
          <button class="ghost" @click="searchAccounts">查询账户</button>
        </div>
      </header>

      <div v-if="errorMessage" class="error">{{ errorMessage }}</div>

      <section v-if="activeView === 'overview'">
        <div class="hero panel">
          <div>
            <span>DEPOSIT LIFECYCLE</span>
            <h2>把保证金做成可运营、可追踪、可核对的资金工作台</h2>
            <p>支持开户、收取、冻结、解冻、扣罚、退还、欠款抵扣、账户查询和流水查询。</p>
          </div>
          <button class="primary" @click="activeView = 'accounts'">进入账户作业</button>
        </div>

        <div class="metrics">
          <article><span>账户总数</span><strong>{{ overview.accountCount }}</strong><small>独立保证金账户</small></article>
          <article class="green"><span>保证金余额</span><strong>¥{{ totalBalance }}</strong><small>账户余额合计</small></article>
          <article class="warm"><span>冻结金额</span><strong>¥{{ totalFrozen }}</strong><small>不可用余额</small></article>
          <article><span>可用金额</span><strong>¥{{ totalAvailable }}</strong><small>可操作金额</small></article>
        </div>

        <div class="panel process">
          <span>CONTROL RULES</span>
          <h3>保证金生命周期</h3>
          <div><b>开户</b><i>→</i><b>收取</b><i>→</i><b>冻结 / 解冻</b><i>→</i><b>扣罚 / 抵扣</b><i>→</i><b>退还</b></div>
        </div>
      </section>

      <section v-else-if="activeView === 'v2'" class="panel">
        <div class="section-head">
          <div>
            <span>DEPOSIT V2</span>
            <h2>总保证金账户演进预览</h2>
          </div>
          <button class="primary" @click="loadV2Preview">刷新预览</button>
        </div>

        <div class="metrics">
          <article><span>总账户数</span><strong>{{ v2Overview.totalAccountCount }}</strong><small>按主体类型归集</small></article>
          <article class="green"><span>规则绑定数</span><strong>{{ v2Overview.bindingCount }}</strong><small>一个总账户对应多条规则</small></article>
          <article class="warm"><span>迁移候选</span><strong>{{ v2Overview.migrationCandidateCount }}</strong><small>已有余额的历史账户</small></article>
          <article><span>迁移建议</span><strong>V2 预览</strong><small>尚未正式写库</small></article>
        </div>

        <div class="summary">
          <div><small>总应缴</small><strong>¥{{ v2TotalRequiredAmount }}</strong></div>
          <div><small>总余额</small><strong class="money">¥{{ v2TotalBalance }}</strong></div>
          <div><small>总冻结</small><strong>¥{{ v2TotalFrozen }}</strong></div>
          <div><small>总可用</small><strong>¥{{ v2TotalAvailable }}</strong></div>
        </div>

        <div class="section-head">
          <div>
            <span>TOTAL ACCOUNT PREVIEW</span>
            <h3>总保证金账户列表</h3>
          </div>
        </div>

        <table class="table">
          <thead>
            <tr>
              <th>总账户号</th><th>主体类型</th><th>账户数</th><th>规则数</th><th>迁移候选</th><th>总应缴</th><th>总余额</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in totalAccounts" :key="item.totalAccountNo">
              <td>{{ item.totalAccountNo }}</td>
              <td>{{ item.ownerType }}</td>
              <td>{{ item.accountCount }}</td>
              <td>{{ item.ruleCount }}</td>
              <td>{{ item.migrationCount }}</td>
              <td>¥{{ Number(item.totalRequiredAmount || 0).toFixed(2) }}</td>
              <td>¥{{ Number(item.totalBalance || 0).toFixed(2) }}</td>
              <td><em>{{ item.status }}</em></td>
              <td><button class="link" @click="selectTotalAccount(item)">查看详情</button></td>
            </tr>
          </tbody>
        </table>

        <div class="detail" v-if="totalAccountDetail">
          <div class="panel">
            <div class="section-head">
              <div>
                <span>TOTAL ACCOUNT DETAIL</span>
                <h2>{{ totalAccountDetail.summary.totalAccountNo }}</h2>
              </div>
            </div>
            <div class="summary">
              <div><small>主体类型</small><strong>{{ totalAccountDetail.summary.ownerType }}</strong></div>
              <div><small>账户数</small><strong>{{ totalAccountDetail.summary.accountCount }}</strong></div>
              <div><small>规则数</small><strong>{{ totalAccountDetail.summary.ruleCount }}</strong></div>
              <div><small>结转候选</small><strong>{{ totalAccountDetail.summary.migrationCount }}</strong></div>
            </div>
            <table class="table">
              <thead>
                <tr>
                  <th>规则编码</th><th>规则名称</th><th>来源账户</th><th>应缴金额</th><th>当前余额</th><th>绑定状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in totalAccountDetail.bindings" :key="item.ruleCode">
                  <td>{{ item.ruleCode }}</td>
                  <td>{{ item.ruleName }}</td>
                  <td>{{ item.sourceAccountNo }}</td>
                  <td>¥{{ Number(item.ruleAmount || 0).toFixed(2) }}</td>
                  <td>¥{{ Number(item.currentBalance || 0).toFixed(2) }}</td>
                  <td>{{ item.bindingStatus }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="panel">
            <div class="section-head">
              <div>
                <span>MIGRATION PREVIEW</span>
                <h3>旧账户结转预览</h3>
              </div>
            </div>
            <table class="table">
              <thead>
                <tr>
                  <th>结转号</th><th>来源账户</th><th>迁移金额</th><th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in totalAccountDetail.migrations" :key="item.migrationNo">
                  <td>{{ item.migrationNo }}</td>
                  <td>{{ item.sourceAccountNo }}</td>
                  <td>¥{{ Number(item.migratedBalance || 0).toFixed(2) }}</td>
                  <td>{{ item.migrationStatus }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="panel" v-if="migrations.length">
          <div class="section-head">
            <div>
              <span>MIGRATION BOARD</span>
              <h3>结转预览池</h3>
            </div>
          </div>
          <table class="table">
            <thead>
              <tr>
                <th>结转号</th><th>来源主体</th><th>来源账户</th><th>目标总账户</th><th>迁移金额</th><th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in migrations" :key="item.migrationNo">
                <td>{{ item.migrationNo }}</td>
                <td>{{ item.sourceOwnerId }} / {{ item.sourceOwnerType }}</td>
                <td>{{ item.sourceAccountNo }}</td>
                <td>{{ item.targetTotalAccountNo }}</td>
                <td>¥{{ Number(item.migratedBalance || 0).toFixed(2) }}</td>
                <td>{{ item.migrationRemark }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="activeView === 'accounts'" class="panel">
        <div class="section-head">
          <div>
            <span>DEPOSIT ACCOUNTS</span>
            <h2>保证金账户</h2>
          </div>
          <div class="top-actions">
            <button class="ghost" @click="exportAccounts">导出清单</button>
            <button class="primary" @click="activeView = 'create'">新开账户</button>
          </div>
        </div>

        <div class="filters">
          <select v-model="accountFilter.ownerType">
            <option value="">全部主体类型</option>
            <option value="WORKER">WORKER</option>
            <option value="MERCHANT">MERCHANT</option>
            <option value="PLATFORM">PLATFORM</option>
          </select>
          <select v-model="accountFilter.status">
            <option value="">全部状态</option>
            <option value="OPEN">OPEN</option>
          </select>
          <input v-model="accountFilter.keyword" placeholder="账户号 / 主体编号" />
        </div>

        <table class="table">
          <thead>
            <tr>
              <th>账户号</th><th>主体</th><th>应缴金额</th><th>余额</th><th>冻结</th><th>可用</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in accounts" :key="item.accountNo">
              <td><button class="link" @click="selectAccount(item)">{{ item.accountNo }}</button></td>
              <td>{{ item.ownerId }}<small>{{ item.ownerType }}</small></td>
              <td>¥{{ Number(item.requiredAmount).toFixed(2) }}</td>
              <td class="money">¥{{ Number(item.balance).toFixed(2) }}</td>
              <td>¥{{ Number(item.frozenAmount).toFixed(2) }}</td>
              <td>¥{{ Number(item.availableAmount).toFixed(2) }}</td>
              <td><em>{{ item.status }}</em></td>
              <td><button class="link" @click="selectAccount(item)">查看详情</button></td>
            </tr>
          </tbody>
        </table>
        <div v-if="!accounts.length && !loading" class="empty">暂无保证金账户</div>
      </section>

      <section v-else-if="activeView === 'create'" class="panel form-panel">
        <span>OPEN ACCOUNT</span>
        <h2>新开保证金账户</h2>
        <label>主体编号<input v-model="form.ownerId" placeholder="WORKER-1001" /></label>
        <label>主体类型
          <select v-model="form.ownerType">
            <option>WORKER</option>
            <option>MERCHANT</option>
            <option>PLATFORM</option>
          </select>
        </label>
        <label>应缴金额<input v-model.number="form.requiredAmount" type="number" min="0" /></label>
        <div class="actions">
          <button class="primary" @click="create">创建账户</button>
          <button class="ghost" @click="activeView = 'accounts'">取消</button>
        </div>
      </section>

      <section v-else class="detail">
        <div class="panel" v-if="selectedDetail">
          <div class="section-head">
            <div>
              <span>ACCOUNT DETAIL</span>
              <h2>{{ selectedDetail.accountNo }}</h2>
            </div>
            <button class="ghost" @click="activeView = 'accounts'">返回列表</button>
          </div>

          <div class="summary">
            <div><small>主体</small><strong>{{ selectedDetail.ownerId }}</strong></div>
            <div><small>余额</small><strong class="money">¥{{ Number(selectedDetail.balance || 0).toFixed(2) }}</strong></div>
            <div><small>冻结</small><strong>¥{{ Number(selectedDetail.frozenAmount || 0).toFixed(2) }}</strong></div>
            <div><small>可用</small><strong>¥{{ Number(selectedDetail.availableAmount || 0).toFixed(2) }}</strong></div>
            <div><small>流水笔数</small><strong>{{ selectedDetail.flowCount || 0 }}</strong></div>
            <div><small>最后流水</small><strong>{{ selectedDetail.latestFlowTime?.replace("T", " ") || "-" }}</strong></div>
            <div><small>已缴达标</small><strong>{{ selectedDetail.fullyPaid ? "是" : "否" }}</strong></div>
            <div><small>待补缴</small><strong>¥{{ Number(selectedDetail.outstandingAmount || 0).toFixed(2) }}</strong></div>
          </div>

          <div class="operation-form">
            <select v-model="action.type">
              <option v-for="(label, type) in actionLabels" :key="type" :value="type">{{ label }}</option>
            </select>
            <input v-model="action.amount" type="number" min="0" placeholder="金额" />
            <input v-model="action.referenceNo" placeholder="业务关联号" />
            <input v-model="action.remark" placeholder="备注" />
            <button class="primary" @click="runAction">执行动作</button>
          </div>

          <div class="operation-form offset">
            <input v-model="debt.debtNo" placeholder="欠款单号" />
            <input v-model="debt.debtAmount" type="number" min="0" placeholder="抵扣金额" />
            <input v-model="debt.remark" placeholder="抵扣备注" />
            <button class="secondary" @click="runOffset">抵扣欠款</button>
          </div>
        </div>

        <div class="panel">
          <div class="section-head">
            <div>
              <span>DEPOSIT FLOWS</span>
              <h3>资金流水</h3>
            </div>
            <div class="flow-filters">
              <select v-model="flowFilter.flowType">
                <option value="">全部类型</option>
                <option value="COLLECT">COLLECT</option>
                <option value="FREEZE">FREEZE</option>
                <option value="UNFREEZE">UNFREEZE</option>
                <option value="DEDUCT">DEDUCT</option>
                <option value="REFUND">REFUND</option>
                <option value="OFFSET_DEBT">OFFSET_DEBT</option>
              </select>
              <input v-model="flowFilter.referenceNo" placeholder="关联号筛选" />
              <button class="ghost" @click="applyFlowFilter">筛选流水</button>
              <button class="ghost" @click="exportFlows">导出流水</button>
            </div>
          </div>
          <table class="table">
            <thead>
              <tr>
                <th>流水号</th><th>类型</th><th>发生金额</th><th>余额变化</th><th>冻结变化</th><th>关联号</th><th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in flows" :key="item.flowNo">
                <td>{{ item.flowNo }}</td>
                <td><em>{{ item.flowType }}</em></td>
                <td class="money">¥{{ Number(item.amount).toFixed(2) }}</td>
                <td>¥{{ Number(item.beforeBalance).toFixed(2) }} → ¥{{ Number(item.afterBalance).toFixed(2) }}</td>
                <td>¥{{ Number(item.beforeFrozenAmount || 0).toFixed(2) }} → ¥{{ Number(item.afterFrozenAmount || 0).toFixed(2) }}</td>
                <td>{{ item.referenceNo || "-" }}</td>
                <td>{{ item.createdAt?.replace("T", " ") }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!flows.length" class="empty">暂无流水</div>
        </div>
      </section>
    </main>
  </div>
</template>
