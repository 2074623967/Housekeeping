<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { accountApi, subjectApi } from "../api/client";

const router = useRouter();
const rows = ref([]);
const subjects = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loading = ref(false);
const message = ref("");
const detailLoading = ref(false);
const detailMessage = ref("");
const selectedAccount = ref(null);
const filters = ref({ subjectId: "", accountType: "", status: "" });
const form = ref({ subjectId: "", accountType: "USER_CASH", currency: "CNY" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const [result, subjectResult] = await Promise.all([
      accountApi.getList({ ...filters.value, pageNo: pageNo.value, pageSize }),
      subjectApi.getList({ pageNo: 1, pageSize: 100 })
    ]);
    rows.value = result.items;
    total.value = result.total;
    subjects.value = subjectResult.items;
    if (!form.value.subjectId && subjects.value.length) {
      form.value.subjectId = subjects.value[0].subjectId;
    }
    await refreshPreview(result.items);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function openAccount() {
  message.value = "";
  try {
    await accountApi.open(form.value);
    await loadRows();
  } catch (error) {
    message.value = error.message;
  }
}

async function loadDetail(accountNo) {
  detailLoading.value = true;
  detailMessage.value = "";
  try {
    selectedAccount.value = await accountApi.getDetail(accountNo);
  } catch (error) {
    selectedAccount.value = null;
    detailMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

async function refreshPreview(accountRows) {
  if (!accountRows.length) {
    selectedAccount.value = null;
    detailMessage.value = "";
    return;
  }
  const currentAccountNo = selectedAccount.value?.account?.accountNo;
  const matchedRow = accountRows.find((item) => item.accountNo === currentAccountNo);
  const targetRow = matchedRow || accountRows[0];
  await loadDetail(targetRow.accountNo);
}

async function previewDetail(row) {
  await loadDetail(row.accountNo);
}

function openDetailPage(row) {
  router.push(`/accounts/${row.accountNo}`);
}

function resetFilters() {
  pageNo.value = 1;
  filters.value = { subjectId: "", accountType: "", status: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账户管理</h2>
        <p>账户开立、状态、余额与明细查询</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>主体ID</label><input v-model="filters.subjectId" placeholder="主体ID" /></div>
        <div class="field"><label>账户类型</label><input v-model="filters.accountType" placeholder="USER_CASH / WORKER_INCENTIVE" /></div>
        <div class="field"><label>账户状态</label><input v-model="filters.status" placeholder="正常 / 关闭" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="toolbar">
        <div class="field">
          <label>选择主体</label>
          <select v-model="form.subjectId">
            <option v-for="subject in subjects" :key="subject.subjectId" :value="subject.subjectId">
              {{ subject.subjectName }}（{{ subject.subjectId }}）
            </option>
          </select>
        </div>
        <div class="field"><label>账户类型</label><input v-model="form.accountType" /></div>
        <div class="field"><label>币种</label><input v-model="form.currency" /></div>
        <div class="toolbar-actions">
          <button class="button warn" @click="openAccount">开立账户</button>
        </div>
      </div>

      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">账户列表加载中...</div>
      <div v-else class="split-panels">
        <div class="table-wrap">
          <table>
            <thead><tr><th>账户号</th><th>主体</th><th>账户类型</th><th>状态</th><th>币种</th><th>可用</th><th>冻结</th><th>在途</th><th>变更时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.accountNo">
                <td>{{ row.accountNo }}</td><td>{{ row.subjectName }}</td><td>{{ row.accountType }}</td>
                <td><span class="badge" :class="row.statusType">{{ row.accountStatus }}</span></td>
                <td>{{ row.currency }}</td><td>{{ row.availableAmount }}</td><td>{{ row.frozenAmount }}</td><td>{{ row.inTransitAmount }}</td>
                <td>{{ row.lastChangeAt }}</td>
                <td>
                  <div class="toolbar-actions">
                    <button class="button secondary" @click="previewDetail(row)">快速查看</button>
                    <button class="button secondary" @click="openDetailPage(row)">详情页</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel" style="margin-bottom:0">
          <div class="section-head">
            <h3 style="margin:0">快速详情</h3>
          </div>
          <div v-if="detailMessage" class="state-box">{{ detailMessage }}</div>
          <div v-else-if="detailLoading" class="state-box">账户详情加载中...</div>
          <template v-else-if="selectedAccount">
            <div class="detail-grid">
              <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ selectedAccount.account.accountNo }}</div></div>
              <div class="detail-card"><div class="detail-label">主体</div><div class="detail-value">{{ selectedAccount.account.subjectName }}</div></div>
              <div class="detail-card"><div class="detail-label">账户状态</div><div class="detail-value"><span class="badge" :class="selectedAccount.account.statusType">{{ selectedAccount.account.accountStatus }}</span></div></div>
              <div class="detail-card"><div class="detail-label">可用余额</div><div class="detail-value">{{ selectedAccount.balance.availableAmount }}</div></div>
              <div class="detail-card"><div class="detail-label">冻结余额</div><div class="detail-value">{{ selectedAccount.balance.frozenAmount }}</div></div>
              <div class="detail-card"><div class="detail-label">在途余额</div><div class="detail-value">{{ selectedAccount.balance.inTransitAmount }}</div></div>
              <div class="detail-card"><div class="detail-label">最近变更时间</div><div class="detail-value">{{ selectedAccount.account.lastChangeAt }}</div></div>
              <div class="detail-card"><div class="detail-label">币种</div><div class="detail-value">{{ selectedAccount.account.currency }}</div></div>
              <div class="detail-card"><div class="detail-label">最近流水数</div><div class="detail-value">{{ selectedAccount.recentLedgers.length }}</div></div>
            </div>
            <div v-if="selectedAccount.recentLedgers.length" class="panel" style="margin-top:16px;margin-bottom:0;padding:0">
              <div class="table-wrap">
                <table>
                  <thead><tr><th>最近流水号</th><th>业务类型</th><th>方向</th><th>金额</th><th>状态</th><th>时间</th></tr></thead>
                  <tbody>
                    <tr v-for="item in selectedAccount.recentLedgers.slice(0, 3)" :key="item.ledgerNo">
                      <td>{{ item.ledgerNo }}</td>
                      <td>{{ item.bizType }}</td>
                      <td>{{ item.direction }}</td>
                      <td>{{ item.amount }}</td>
                      <td><span class="badge" :class="item.statusType">{{ item.ledgerStatus }}</span></td>
                      <td>{{ item.createdAt }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </template>
          <div v-else class="state-box">点击左侧“快速查看”后，可在这里直接预览余额和账户状态。</div>
        </div>
      </div>
    </section>
  </div>
</template>
