<script setup>
import { onMounted, ref } from "vue";
import { refundApi } from "../api/client";

const items = ref([]);

onMounted(async () => {
  items.value = await refundApi.getList();
});
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>退款单管理</h2>
        <p>查看标准退款、部分退款和已结算后退款场景</p>
      </div>
      <span class="badge warn">待处理 18 笔</span>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="field">
          <label>退款单号</label>
          <input placeholder="请输入退款单号" />
        </div>
        <div class="field">
          <label>原支付单号</label>
          <input placeholder="请输入原支付单号" />
        </div>
        <div class="field">
          <label>退款状态</label>
          <select>
            <option>全部</option>
            <option>PROCESSING</option>
            <option>SUCCESS</option>
            <option>FAIL</option>
          </select>
        </div>
        <div class="field">
          <label>退款原因</label>
          <select>
            <option>全部</option>
            <option>用户取消</option>
            <option>投诉赔付</option>
            <option>补偿退款</option>
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
              <th>退款单号</th>
              <th>原支付单号</th>
              <th>原订单号</th>
              <th>用户</th>
              <th>退款金额</th>
              <th>退款方式</th>
              <th>退款状态</th>
              <th>申请时间</th>
              <th>成功时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.refundOrderId">
              <td>{{ item.refundOrderId }}</td>
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.refundAmount }}</td>
              <td>{{ item.refundMethod }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td>{{ item.appliedAt }}</td>
              <td>{{ item.successAt }}</td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>重试</span>
                  <span>退转付</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
