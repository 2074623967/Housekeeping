<script setup>
import { onMounted, ref } from "vue";
import { paymentApi } from "../api/client";

const items = ref([]);

onMounted(async () => {
  items.value = await paymentApi.getList();
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付单管理</h2>
        <p>统一查看支付单、支付方式、渠道交易和回调收敛情况</p>
      </div>
      <button class="button primary">导出支付单</button>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select>
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行卡</option>
          </select>
        </div>
        <div class="field">
          <label>支付状态</label>
          <select>
            <option>全部</option>
            <option>SUCCESS</option>
            <option>WAIT_CALLBACK</option>
            <option>FAIL</option>
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
              <th>支付单号</th>
              <th>订单号</th>
              <th>用户</th>
              <th>实付金额</th>
              <th>支付方式</th>
              <th>支付渠道</th>
              <th>渠道交易号</th>
              <th>支付状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.paymentOrderId">
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.amount }}</td>
              <td>{{ item.paymentMethod }}</td>
              <td>{{ item.channel }}</td>
              <td>{{ item.channelTransactionNo }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td>{{ item.createdAt }}</td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>主动查询</span>
                  <span>回调</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
