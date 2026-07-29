<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { accountApi, balanceApi, ledgerApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const message = ref("");
const loading = ref(true);

const accountNo = computed(() => route.params.accountNo);

const form = ref({ bizNo: "", bizType: "MANUAL_CREDIT", amount: "10.00", operatorName: "运营小王", remark: "测试冻结" });

async function loadDetail() {
  loading.value = true;
  message.value = "";
  try {
    detail.value = await accountApi.getDetail(accountNo.value);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function credit() {
  await balanceApi.credit({ accountNo: accountNo.value, bizNo: form.value.bizNo || `BIZ-${Date.now()}`, bizType: form.value.bizType, amount: Number(form.value.amount), operatorName: form.value.operatorName });
  await loadDetail();
}

async function freeze() {
  await balanceApi.freeze({ accountNo: accountNo.value, bizNo: form.value.bizNo || `FRZ-${Date.now()}`, bizType: "FUND_FREEZE", amount: Number(form.value.amount), operatorName: form.value.operatorName, remark: form.value.remark });
  await loadDetail();
}

async function unfreeze() {
  await balanceApi.unfreeze({ accountNo: accountNo.value, bizNo: form.value.bizNo || `FRZ-${Date.now()}`, bizType: "FUND_UNFREEZE", amount: Number(form.value.amount), operatorName: form.value.operatorName, remark: "自动解冻" });
  await loadDetail();
}

function openLedgers() {
  router.push({ path: "/ledgers", query: { accountNo: accountNo.value } });
}

onMounted(loadDetail);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账户详情</h2>
        <p>余额快照、最近流水和资金操作入口</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push('/accounts')">返回列表</button>
        <button class="button secondary" @click="openLedgers">看流水</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">账户详情加载中...</div>
      <template v-else>
        <div class="detail-grid">
          <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ detail.account.accountNo }}</div></div>
          <div class="detail-card"><div class="detail-label">主体</div><div class="detail-value">{{ detail.account.subjectName }}</div></div>
          <div class="detail-card"><div class="detail-label">账户状态</div><div class="detail-value"><span class="badge" :class="detail.account.statusType">{{ detail.account.accountStatus }}</span></div></div>
          <div class="detail-card"><div class="detail-label">可用余额</div><div class="detail-value">{{ detail.balance.availableAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">冻结余额</div><div class="detail-value">{{ detail.balance.frozenAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">在途余额</div><div class="detail-value">{{ detail.balance.inTransitAmount }}</div></div>
        </div>

        <div class="toolbar" style="margin-top:16px">
          <div class="field"><label>业务单号</label><input v-model="form.bizNo" placeholder="可选" /></div>
          <div class="field"><label>金额</label><input v-model="form.amount" /></div>
          <div class="field"><label>操作人</label><input v-model="form.operatorName" /></div>
          <div class="toolbar-actions">
            <button class="button primary" @click="credit">余额入账</button>
            <button class="button warn" @click="freeze">余额冻结</button>
            <button class="button secondary" @click="unfreeze">余额解冻</button>
          </div>
        </div>

        <section class="panel" style="padding:0;margin-top:18px">
          <div class="table-wrap">
            <table>
              <thead><tr><th>流水号</th><th>业务类型</th><th>业务单号</th><th>方向</th><th>金额</th><th>变更前</th><th>变更后</th><th>状态</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-for="item in detail.recentLedgers" :key="item.ledgerNo">
                  <td>{{ item.ledgerNo }}</td><td>{{ item.bizType }}</td><td>{{ item.bizNo }}</td><td>{{ item.direction }}</td><td>{{ item.amount }}</td><td>{{ item.beforeBalance }}</td><td>{{ item.afterBalance }}</td><td><span class="badge" :class="item.statusType">{{ item.ledgerStatus }}</span></td><td>{{ item.createdAt }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </section>
  </div>
</template>
