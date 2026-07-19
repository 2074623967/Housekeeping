<script setup>
import { onMounted, ref } from "vue";
import { orderApi } from "../api/client";

const items = ref([]);

onMounted(async () => {
  items.value = await orderApi.getList();
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>订单中心</h2>
        <p>查看家政订单、支付状态和履约进展</p>
      </div>
      <button class="button primary">导出订单</button>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>订单号</label>
          <input placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>用户 ID</label>
          <input placeholder="请输入用户 ID" />
        </div>
        <div class="field">
          <label>服务品类</label>
          <select>
            <option>全部</option>
            <option>保洁</option>
            <option>月嫂</option>
            <option>企业保洁</option>
          </select>
        </div>
        <div class="field">
          <label>订单状态</label>
          <select>
            <option>全部</option>
            <option>待支付</option>
            <option>待履约</option>
            <option>已完成</option>
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
              <th>订单号</th>
              <th>用户</th>
              <th>服务品类</th>
              <th>服务者</th>
              <th>订单金额</th>
              <th>已付金额</th>
              <th>订单状态</th>
              <th>履约状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.orderNo">
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.serviceType }}</td>
              <td>{{ item.workerName }}</td>
              <td>{{ item.orderAmount }}</td>
              <td>{{ item.paidAmount }}</td>
              <td><span :class="['badge', item.orderStatusType]">{{ item.orderStatus }}</span></td>
              <td><span :class="['badge', item.fulfillmentStatusType]">{{ item.fulfillmentStatus }}</span></td>
              <td>{{ item.createdAt }}</td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>账单</span>
                  <span>补差价</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
