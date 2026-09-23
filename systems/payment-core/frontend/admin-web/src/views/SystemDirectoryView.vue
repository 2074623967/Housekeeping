<script setup>
import { computed } from "vue";
import { systemCatalog, systemGroups } from "../constants/systemCatalog";

const groups = computed(() => systemGroups());

function itemsForGroup(group) {
  return systemCatalog.filter((item) => item.group === group);
}
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>系统总览</h2>
        <p>从这里进入支付、资金和平台治理的本地可运行入口。</p>
      </div>
      <span class="badge success">本地骨架已启动</span>
    </div>

    <section class="panel system-directory-panel">
      <div class="system-directory-note">
        <strong>当前工作区</strong>
        <span>所有入口都指向当前本地栈；部署到其他环境时通过 Vite 环境变量替换地址。</span>
      </div>

      <section v-for="group in groups" :key="group" class="system-group">
        <div class="system-group-heading">
          <h3>{{ group }}</h3>
          <span>{{ itemsForGroup(group).length }} 个入口</span>
        </div>
        <div class="system-directory-table-wrap">
          <table class="system-directory-table">
            <thead>
              <tr>
                <th>系统</th>
                <th>职责</th>
                <th>本地地址</th>
                <th>类型</th>
                <th>入口</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in itemsForGroup(group)" :key="item.code">
                <td>
                  <strong>{{ item.name }}</strong>
                  <small>{{ item.code }}</small>
                </td>
                <td>{{ item.description }}</td>
                <td><code>{{ item.baseUrl }}</code></td>
                <td>
                  <span class="badge" :class="item.kind === 'admin' ? 'info' : item.kind === 'terminal' ? 'success' : 'warn'">
                    {{ item.kind === "admin" ? "后台" : item.kind === "terminal" ? "用户端" : "接入端" }}
                  </span>
                </td>
                <td>
                  <a v-if="item.kind !== 'integration'" class="button secondary system-open-link" :href="item.baseUrl" target="_blank" rel="noreferrer">
                    打开
                  </a>
                  <div v-else class="integration-entry">
                    <span>{{ item.entryType === "path" ? "工程路径" : "联调命令" }}</span>
                    <code>{{ item.entryValue }}</code>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.system-directory-panel {
  overflow: hidden;
}

.system-directory-note {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 0 0 18px;
  color: #64748b;
}

.system-directory-note strong {
  color: #0f172a;
}

.system-group + .system-group {
  margin-top: 28px;
}

.system-group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.system-group-heading h3 {
  margin: 0;
  font-size: 17px;
}

.system-group-heading span {
  color: #64748b;
  font-size: 13px;
}

.system-directory-table-wrap {
  overflow-x: auto;
}

.system-directory-table td:first-child {
  min-width: 180px;
}

.system-directory-table td:nth-child(2) {
  min-width: 240px;
}

.system-directory-table small {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.system-directory-table code {
  color: #475569;
  font-size: 12px;
}

.system-open-link {
  display: inline-flex;
  padding: 7px 12px;
}

.integration-entry {
  display: grid;
  gap: 4px;
  color: #64748b;
  font-size: 12px;
}

.integration-entry code {
  max-width: 360px;
  overflow-wrap: anywhere;
  color: #475569;
}

@media (max-width: 720px) {
  .system-directory-note {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
