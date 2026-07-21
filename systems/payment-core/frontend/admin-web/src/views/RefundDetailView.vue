<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { refundApi } from "../api/client";

const route = useRoute();
const detail = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const actionRemark = ref("");
const activeAction = ref("");

async function loadDetail() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    detail.value = await refundApi.getDetail(route.params.refundOrderId);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

async function runAction(actionName, actionLabel, actionRunner) {
  activeAction.value = actionName;
  actionMessage.value = "";
  try {
    await actionRunner(route.params.refundOrderId, actionRemark.value);
    await loadDetail();
    actionMessage.value = `退款单 ${route.params.refundOrderId} 已${actionLabel}。`;
    actionRemark.value = "";
  } catch (error) {
    actionMessage.value = `退款单 ${route.params.refundOrderId} ${actionLabel}失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

onMounted(loadDetail);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>退款详情</h2>
        <p>统一查看退款原因、原支付快照、状态流转和人工/自动操作日志</p>
      </div>
      <RouterLink class="button secondary" to="/refunds">返回退款单</RouterLink>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        退款详情加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="success-banner">
        {{ actionMessage }}
      </div>
      <div v-if="isLoading" class="state-box">退款详情加载中...</div>

      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">退款单号</div>
            <div class="detail-value">{{ detail.refundOrderId }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">退款状态</div>
            <div class="detail-value">
              <span :class="['badge', detail.statusType]">{{ detail.status }}</span>
            </div>
          </div>
          <div class="detail-card">
            <div class="detail-label">退款金额</div>
            <div class="detail-value">{{ detail.refundAmount }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">退款方式</div>
            <div class="detail-value">{{ detail.refundMethod }}</div>
          </div>
        </div>

        <div class="split-panels">
          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>退款基础信息</h3>
                <p class="meta">财务与运营先确认退款原因、原支付单和申请时间</p>
              </div>
            </div>
            <div class="detail-grid">
              <div><strong>原支付单号：</strong>{{ detail.paymentOrderId }}</div>
              <div><strong>原订单号：</strong>{{ detail.orderNo }}</div>
              <div><strong>客户名称：</strong>{{ detail.customerName }}</div>
              <div><strong>申请时间：</strong>{{ detail.appliedAt }}</div>
              <div><strong>成功时间：</strong>{{ detail.successAt }}</div>
              <div><strong>退款原因：</strong>{{ detail.refundReason || "-" }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <div>
                <h3>原支付快照</h3>
                <p class="meta">确认原支付金额、方式和状态，避免误退款</p>
              </div>
            </div>
            <div class="detail-grid">
              <div><strong>原支付金额：</strong>{{ detail.paidAmount }}</div>
              <div><strong>原支付方式：</strong>{{ detail.paymentMethod }}</div>
              <div><strong>原支付状态：</strong>{{ detail.paymentStatus }}</div>
            </div>
          </section>
        </div>

        <div class="sub-panel">
          <h3>退款动作</h3>
          <p>操作备注会进入退款操作日志，便于财务、测试和研发统一复盘。</p>
          <div class="toolbar compact-toolbar">
            <div class="field wide-field">
              <label>操作备注</label>
              <input v-model="actionRemark" placeholder="请输入审核意见、失败原因或重试说明" />
            </div>
            <div class="toolbar-actions">
              <button class="button primary" :disabled="activeAction === 'approve' || detail.status !== 'REVIEWING'" @click="runAction('approve', '审核通过', refundApi.approve)">审核通过</button>
              <button class="button primary" :disabled="activeAction === 'success' || detail.status !== 'PROCESSING'" @click="runAction('success', '退款成功', refundApi.markSuccess)">成功回调</button>
              <button class="button secondary" :disabled="activeAction === 'fail' || detail.status !== 'PROCESSING'" @click="runAction('fail', '退款失败', refundApi.markFail)">失败回调</button>
              <button class="button secondary" :disabled="activeAction === 'retry' || detail.status !== 'FAIL'" @click="runAction('retry', '重试', refundApi.retry)">失败重试</button>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>退款操作日志</h3>
              <p class="meta">统一沉淀人工申请、审核、渠道回调和任务中心重试动作</p>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>日志号</th>
                  <th>动作编码</th>
                  <th>动作名称</th>
                  <th>原状态</th>
                  <th>目标状态</th>
                  <th>操作人</th>
                  <th>操作备注</th>
                  <th>操作时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.operationLogs" :key="item.logNo">
                  <td>{{ item.logNo }}</td>
                  <td>{{ item.actionCode }}</td>
                  <td>{{ item.actionName }}</td>
                  <td>{{ item.fromStatus }}</td>
                  <td>{{ item.toStatus }}</td>
                  <td>{{ item.operatorName }}</td>
                  <td class="flow-summary-cell">{{ item.operationRemark }}</td>
                  <td>{{ item.createdAt }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
