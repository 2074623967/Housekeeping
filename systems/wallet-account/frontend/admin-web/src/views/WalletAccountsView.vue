<script setup>
import { onMounted, reactive, ref } from "vue";
import {
  changeWalletAccountStatus,
  fetchWalletAccountDetail,
  fetchWalletAccounts,
  openWalletAccount
} from "../api";

const query = reactive({
  keyword: "",
  ownerType: "",
  accountStatus: "",
  pageNo: 1,
  pageSize: 20
});

const accounts = ref([]);
const recentFlows = ref([]);
const statusLogs = ref([]);
const balance = ref(null);
const detailAccount = ref(null);
const loading = ref(false);
const errorMessage = ref("");
const actionMessage = ref("");
const showOpenDialog = ref(false);
const opening = ref(false);
const changingStatus = ref(false);
const openForm = reactive({
  requestNo: "",
  walletOwnerId: "",
  ownerType: "USER",
  ownerName: "",
  extRefNo: "",
  bizLineCode: "HOME_SERVICE",
  tenantCode: "DEFAULT",
  accountType: "MAIN",
  accountScene: "USER_STORE",
  currencyCode: "CNY",
  operatorId: "admin",
  operatorName: "运营管理员"
});

async function loadAccounts() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const data = await fetchWalletAccounts(query);
    accounts.value = data.records || [];
    if (accounts.value.length > 0) {
      await loadDetail(accounts.value[0].walletAccountNo);
    } else {
      detailAccount.value = null;
      balance.value = null;
      recentFlows.value = [];
      statusLogs.value = [];
    }
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function loadDetail(walletAccountNo) {
  try {
    const data = await fetchWalletAccountDetail(walletAccountNo);
    detailAccount.value = data.account;
    balance.value = data.balance;
    recentFlows.value = data.recentFlows || [];
    statusLogs.value = data.statusLogs || [];
  } catch (error) {
    errorMessage.value = error.message;
  }
}

function startOpenAccount() {
  Object.assign(openForm, {
    requestNo: `REQ-${Date.now()}`,
    walletOwnerId: "",
    ownerType: "USER",
    ownerName: "",
    extRefNo: "",
    accountType: "MAIN",
    accountScene: "USER_STORE"
  });
  actionMessage.value = "";
  showOpenDialog.value = true;
}

async function submitOpenAccount() {
  opening.value = true;
  actionMessage.value = "";
  try {
    await openWalletAccount(openForm);
    showOpenDialog.value = false;
    actionMessage.value = "开户成功，列表已刷新";
    await loadAccounts();
  } catch (error) {
    actionMessage.value = error.message;
  } finally {
    opening.value = false;
  }
}

function statusAction(account) {
  if (account.accountStatus === "ACTIVE") {
    return "FROZEN";
  }
  if (account.accountStatus === "FROZEN") {
    return "ACTIVE";
  }
  return null;
}

async function changeStatus(account, targetStatus) {
  const actionText = targetStatus === "FROZEN" ? "冻结" : "解冻";
  if (!window.confirm(`确认${actionText}账户 ${account.walletAccountNo} 吗？`)) {
    return;
  }
  changingStatus.value = true;
  actionMessage.value = "";
  try {
    await changeWalletAccountStatus(account.walletAccountNo, {
      targetStatus,
      operatorId: "admin",
      operatorName: "运营管理员",
      operationReason: `${actionText}账户`
    });
    actionMessage.value = `${actionText}成功`;
    await loadAccounts();
    await loadDetail(account.walletAccountNo);
  } catch (error) {
    actionMessage.value = error.message;
  } finally {
    changingStatus.value = false;
  }
}

onMounted(() => {
  loadAccounts();
});
</script>

<template>
  <section class="page">
    <div class="layout">
      <div class="panel">
        <h2>钱包账户列表</h2>
        <p class="muted">账户查询、开户和状态流转均通过 wallet-account 真实接口完成。</p>
        <div class="toolbar">
          <input v-model="query.keyword" placeholder="账户号 / 主体名称" />
          <select v-model="query.ownerType">
            <option>全部主体</option>
            <option>USER</option>
            <option>WORKER</option>
          </select>
          <select v-model="query.accountStatus">
            <option>全部状态</option>
            <option>ACTIVE</option>
            <option>FROZEN</option>
            <option>CLOSED</option>
          </select>
          <button class="button" @click="loadAccounts">查询</button>
          <button class="button button--light" type="button" @click="startOpenAccount">新增开户</button>
        </div>
        <p v-if="actionMessage" class="action-message">{{ actionMessage }}</p>
        <p v-if="loading" class="muted">账户加载中...</p>
        <p v-else-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>账户号</th>
                <th>主体</th>
                <th>场景</th>
                <th>状态</th>
                <th>可用余额</th>
                <th>冻结余额</th>
                <th>在途入账</th>
                <th>在途出账</th>
                <th>总余额</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="account in accounts"
                :key="account.walletAccountNo"
                class="clickable-row"
                @click="loadDetail(account.walletAccountNo)"
              >
                <td>{{ account.walletAccountNo }}</td>
                <td>{{ account.ownerName }} / {{ account.ownerType }}</td>
                <td>{{ account.accountScene }}</td>
                <td>
                  <span class="badge" :class="account.accountStatus === 'ACTIVE' ? 'badge--ok' : 'badge--warn'">
                    {{ account.accountStatus }}
                  </span>
                </td>
                <td>{{ account.availableBalance }}</td>
                <td>{{ account.frozenBalance }}</td>
                <td>{{ account.pendingInBalance }}</td>
                <td>{{ account.pendingOutBalance }}</td>
                <td>{{ account.totalBalance }}</td>
                <td class="action-cell" @click.stop>
                  <button
                    v-if="statusAction(account)"
                    class="text-button"
                    :disabled="changingStatus"
                    @click="changeStatus(account, statusAction(account))"
                  >
                    {{ statusAction(account) === "FROZEN" ? "冻结" : "解冻" }}
                  </button>
                  <button
                    v-if="account.accountStatus === 'ACTIVE' || account.accountStatus === 'FROZEN'"
                    class="text-button text-button--danger"
                    :disabled="changingStatus"
                    @click="changeStatus(account, 'CLOSED')"
                  >
                    关闭
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="detail-grid">
        <div class="panel">
          <h3>账户详情骨架</h3>
          <div class="detail-card">
            <div class="detail-label">主体与账户</div>
            <div class="detail-value">
              {{ detailAccount ? `${detailAccount.ownerName} / ${detailAccount.walletAccountNo}` : "暂无详情" }}
            </div>
          </div>
          <div class="kpi-grid">
            <div class="detail-card">
              <div class="detail-label">可用余额</div>
              <div class="detail-value">{{ balance?.availableBalance ?? "--" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">冻结余额</div>
              <div class="detail-value">{{ balance?.frozenBalance ?? "--" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">在途入账</div>
              <div class="detail-value">{{ balance?.pendingInBalance ?? "--" }}</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">在途出账</div>
              <div class="detail-value">{{ balance?.pendingOutBalance ?? "--" }}</div>
            </div>
          </div>
        </div>

        <div class="panel">
          <h3>最近流水</h3>
          <div class="detail-grid">
            <div v-for="flow in recentFlows" :key="flow.flowNo" class="detail-card">
              <div class="detail-label">{{ flow.flowType }} / {{ flow.flowNo }}</div>
              <div class="detail-value">{{ flow.changeAmount }}</div>
              <div class="muted">{{ flow.sourceSystem }} · {{ flow.sourceBizNo }}</div>
            </div>
          </div>
        </div>

        <div class="panel">
          <h3>状态日志</h3>
          <div v-if="statusLogs.length === 0" class="muted">当前账户暂无状态变更日志</div>
          <div v-else class="detail-grid">
            <div v-for="log in statusLogs" :key="`${log.walletAccountNo}-${log.createdAt}`" class="detail-card">
              <div class="detail-label">{{ log.beforeStatus || "EMPTY" }} → {{ log.afterStatus }}</div>
              <div class="detail-value">{{ log.reasonDesc || log.reasonCode || "状态变更" }}</div>
              <div class="muted">{{ log.operatorName || log.operatorId || "system" }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showOpenDialog" class="modal-backdrop" @click.self="showOpenDialog = false">
      <form class="modal" @submit.prevent="submitOpenAccount">
        <div class="modal__header">
          <div>
            <p class="eyebrow">wallet-account</p>
            <h3>新增钱包开户</h3>
          </div>
          <button class="icon-button" type="button" @click="showOpenDialog = false">×</button>
        </div>
        <div class="form-grid">
          <label>主体编号<input v-model="openForm.walletOwnerId" required /></label>
          <label>外部主体编号<input v-model="openForm.extRefNo" required /></label>
          <label>主体名称<input v-model="openForm.ownerName" required /></label>
          <label>主体类型<select v-model="openForm.ownerType"><option>USER</option><option>WORKER</option><option>MERCHANT</option><option>PLATFORM</option></select></label>
          <label>账户类型<select v-model="openForm.accountType"><option>MAIN</option><option>MARKETING</option><option>DEPOSIT</option><option>DEBT_OFFSET</option></select></label>
          <label>账户场景<input v-model="openForm.accountScene" required /></label>
          <label>业务线<input v-model="openForm.bizLineCode" required /></label>
          <label>租户<input v-model="openForm.tenantCode" required /></label>
        </div>
        <div class="modal__actions">
          <button class="button button--light" type="button" @click="showOpenDialog = false">取消</button>
          <button class="button" type="submit" :disabled="opening">{{ opening ? "提交中..." : "确认开户" }}</button>
        </div>
      </form>
    </div>
  </section>
</template>
