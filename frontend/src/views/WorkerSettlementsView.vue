<script setup>
import { onMounted, ref } from "vue";
import { settlementApi } from "../api/client";

const items = ref([]);

onMounted(async () => {
  items.value = await settlementApi.getWorkerList();
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>服务者结算单</h2>
        <p>查看家政服务者的待结算、审核、出款状态</p>
      </div>
      <button class="button primary">发起结算批次</button>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>结算单号</label>
          <input placeholder="请输入结算单号" />
        </div>
        <div class="field">
          <label>服务者 ID</label>
          <input placeholder="请输入服务者 ID" />
        </div>
        <div class="field">
          <label>城市</label>
          <select>
            <option>全部</option>
            <option>上海</option>
            <option>杭州</option>
            <option>北京</option>
          </select>
        </div>
        <div class="field">
          <label>结算状态</label>
          <select>
            <option>全部</option>
            <option>待审核</option>
            <option>待出款</option>
            <option>出款成功</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary">查询</button>
          <button class="button secondary">重置</button>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>结算单号</th>
              <th>服务者</th>
              <th>账期</th>
              <th>应结金额</th>
              <th>已扣减金额</th>
              <th>实结金额</th>
              <th>保证金影响</th>
              <th>结算状态</th>
              <th>出款状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.settlementOrderId">
              <td>{{ item.settlementOrderId }}</td>
              <td>{{ item.workerName }}</td>
              <td>{{ item.period }}</td>
              <td>{{ item.amountShouldSettle }}</td>
              <td>{{ item.deductAmount }}</td>
              <td>{{ item.amountNetSettle }}</td>
              <td>{{ item.depositImpactAmount }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td><span :class="['badge', item.payoutStatusType]">{{ item.payoutStatus }}</span></td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>审核</span>
                  <span>出款</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
