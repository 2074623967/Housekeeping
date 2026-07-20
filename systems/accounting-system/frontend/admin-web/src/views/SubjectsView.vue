<script setup>
import { onMounted, ref } from "vue";
import { subjectApi } from "../api/client";

const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loading = ref(false);
const message = ref("");
const filters = ref({ keyword: "", subjectType: "", status: "" });
const form = ref({ subjectType: "USER", subjectName: "", ownerName: "" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await subjectApi.getList({ ...filters.value, pageNo: pageNo.value, pageSize });
    rows.value = result.items;
    total.value = result.total;
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
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>主体ID</th><th>类型</th><th>名称</th><th>归属人</th><th>状态</th><th>账户数</th><th>创建时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.subjectId">
              <td>{{ row.subjectId }}</td><td>{{ row.subjectType }}</td><td>{{ row.subjectName }}</td><td>{{ row.ownerName }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.status }}</span></td><td>{{ row.linkedAccountCount }}</td><td>{{ row.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
