<script setup>
import { computed, onMounted, ref } from "vue";
import { subjectApi } from "../api/client";

const rows = ref([]);
const selectedRow = ref(null);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loading = ref(false);
const message = ref("");
const filters = ref({ keyword: "", subjectType: "", status: "" });
const form = ref({ subjectType: "USER", subjectName: "", ownerName: "" });

const metrics = computed(() => ({
  total: total.value,
  activeTotal: rows.value.filter((item) => item.status === "启用").length,
  workerTotal: rows.value.filter((item) => item.subjectType === "WORKER").length,
  linkedAccountTotal: rows.value.reduce((sum, item) => sum + Number(item.linkedAccountCount || 0), 0)
}));

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await subjectApi.getList({ ...filters.value, pageNo: pageNo.value, pageSize });
    rows.value = result.items;
    total.value = result.total;
    selectedRow.value = result.items[0] || null;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createSubject() {
  message.value = "";
  try {
    await subjectApi.create(form.value);
    form.value = { subjectType: "USER", subjectName: "", ownerName: "" };
    await loadRows();
  } catch (error) {
    message.value = error.message;
  }
}

function resetFilters() {
  pageNo.value = 1;
  filters.value = { keyword: "", subjectType: "", status: "" };
  loadRows();
}

function pickRow(row) {
  selectedRow.value = row;
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账户主体</h2>
        <p>主体建档、主体状态与账户归属管理</p>
      </div>
    </div>
    <section class="card-grid metric-grid">
      <article class="card">
        <p class="card-title">主体总数</p>
        <p class="card-value">{{ metrics.total }}</p>
      </article>
      <article class="card">
        <p class="card-title">启用主体</p>
        <p class="card-value">{{ metrics.activeTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">服务者主体</p>
        <p class="card-value">{{ metrics.workerTotal }}</p>
      </article>
      <article class="card">
        <p class="card-title">关联账户数</p>
        <p class="card-value">{{ metrics.linkedAccountTotal }}</p>
      </article>
    </section>
    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>关键词</label><input v-model="filters.keyword" placeholder="主体名 / 归属人" />
        </div>
        <div class="field">
          <label>主体类型</label><input v-model="filters.subjectType" placeholder="USER / WORKER / PLATFORM" />
        </div>
        <div class="field">
          <label>状态</label><input v-model="filters.status" placeholder="启用 / 停用" />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>新增主体类型</label><input v-model="form.subjectType" /></div>
        <div class="field"><label>主体名称</label><input v-model="form.subjectName" /></div>
        <div class="field"><label>归属人</label><input v-model="form.ownerName" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createSubject">新增主体</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">主体列表加载中...</div>
      <div v-else class="split-panels">
        <div class="table-wrap">
          <table>
            <thead><tr><th>主体ID</th><th>类型</th><th>名称</th><th>归属人</th><th>状态</th><th>账户数</th><th>创建时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.subjectId">
                <td>{{ row.subjectId }}</td><td>{{ row.subjectType }}</td><td>{{ row.subjectName }}</td><td>{{ row.ownerName }}</td>
                <td><span class="badge" :class="row.statusType">{{ row.status }}</span></td><td>{{ row.linkedAccountCount }}</td><td>{{ row.createdAt }}</td>
                <td><button class="button secondary" @click="pickRow(row)">查看</button></td>
              </tr>
              <tr v-if="rows.length === 0">
                <td colspan="8" class="empty-cell">暂无主体数据。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel" style="margin-bottom:0">
          <div class="section-head">
            <h3 style="margin:0">主体详情</h3>
          </div>
          <template v-if="selectedRow">
            <div class="detail-grid">
              <div class="detail-card"><div class="detail-label">主体ID</div><div class="detail-value">{{ selectedRow.subjectId }}</div></div>
              <div class="detail-card"><div class="detail-label">主体类型</div><div class="detail-value">{{ selectedRow.subjectType }}</div></div>
              <div class="detail-card"><div class="detail-label">状态</div><div class="detail-value"><span class="badge" :class="selectedRow.statusType">{{ selectedRow.status }}</span></div></div>
              <div class="detail-card"><div class="detail-label">主体名称</div><div class="detail-value">{{ selectedRow.subjectName }}</div></div>
              <div class="detail-card"><div class="detail-label">归属人</div><div class="detail-value">{{ selectedRow.ownerName }}</div></div>
              <div class="detail-card"><div class="detail-label">关联账户数</div><div class="detail-value">{{ selectedRow.linkedAccountCount }}</div></div>
            </div>
            <div class="trace-panel">
              <h4>建档建议</h4>
              <ul class="trace-list">
                <li>主体类型需与家政平台业务角色一致，如用户、阿姨、服务商、平台主体。</li>
                <li>启用前需确认实名、签约和后续账户开立策略已准备完成。</li>
                <li>如主体下账户过多或状态异常，应排查重复建档或历史迁移问题。</li>
              </ul>
            </div>
          </template>
          <div v-else class="state-box">选择左侧主体后，可在右侧查看建档信息与检查建议。</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.metric-grid {
  margin-bottom: 18px;
}

.section-head {
  margin-bottom: 12px;
}

.trace-panel {
  margin-top: 16px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.trace-panel h4 {
  margin: 0 0 12px;
}

.trace-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  line-height: 1.7;
}

.empty-cell {
  padding: 16px 0;
  text-align: center;
  color: #64748b;
}
</style>
