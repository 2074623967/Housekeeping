<script setup>
import { computed, onMounted, ref } from "vue";
import { orderApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const detailLoading = ref(false);
const filters = ref({ batchNo: "", orderNo: "", paymentOrderId: "", clearingStatus: "" });
const selectedOrder = ref(null);
const selectedDetail = ref(null);
const detailMessage = ref("");

const detailFeeRules = computed(() => selectedDetail.value?.feeRules || []);
const detailShareItems = computed(() => selectedDetail.value?.shareItems || []);

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await orderApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
    if (selectedOrder.value) {
      const latestOrder = result.items.find((item) => item.clearingNo === selectedOrder.value.clearingNo);
      if (latestOrder) {
        selectedOrder.value = latestOrder;
      }
    }
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function loadDetail(row) {
  selectedOrder.value = row;
  selectedDetail.value = null;
  detailLoading.value = true;
  detailMessage.value = "";
  try {
    selectedDetail.value = await orderApi.getDetail(row.clearingNo);
  } catch (error) {
    detailMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

function resetFilters() {
  filters.value = { batchNo: "", orderNo: "", paymentOrderId: "", clearingStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>清分结果</h2>
        <p>查看每笔支付单的清分结果、费用拆解和分账明细，支撑财务核验与规则回溯</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次号</label><input v-model="filters.batchNo" placeholder="例如：CLB10001" /></div>
        <div class="field"><label>订单号</label><input v-model="filters.orderNo" placeholder="请输入业务订单号" /></div>
        <div class="field"><label>支付单号</label><input v-model="filters.paymentOrderId" placeholder="请输入支付单号" /></div>
        <div class="field"><label>清分状态</label><input v-model="filters.clearingStatus" placeholder="例如：清分成功" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="summary-box">
        <strong>操作说明</strong>
        <span>先在上方定位清分单，再在下方核对费用规则快照和分账明细，确认平台、商家、服务者金额是否闭环。</span>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">清分结果加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>清分单号</th>
              <th>支付单号</th>
              <th>订单号</th>
              <th>订单金额</th>
              <th>商家</th>
              <th>服务者</th>
              <th>平台</th>
              <th>渠道费</th>
              <th>状态</th>
              <th>规则版本</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.clearingNo">
              <td>{{ row.clearingNo }}</td>
              <td>{{ row.paymentOrderId }}</td>
              <td>{{ row.orderNo }}</td>
              <td class="amount">{{ row.orderAmount }}</td>
              <td class="amount">{{ row.merchantAmount }}</td>
              <td class="amount">{{ row.workerAmount }}</td>
              <td class="amount">{{ row.platformAmount }}</td>
              <td class="amount deduct">{{ row.channelFeeAmount }}</td>
              <td><span class="badge" :class="row.clearingStatusType">{{ row.clearingStatus }}</span></td>
              <td>{{ row.ruleNo }}</td>
              <td><button class="button secondary button-inline" :disabled="detailLoading" @click="loadDetail(row)">查看详情</button></td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="11" class="empty-cell">当前筛选条件下暂无清分结果。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="topbar topbar-inner">
        <div>
          <h3>清分详情下钻</h3>
          <p>{{ selectedOrder ? `当前清分单：${selectedOrder.clearingNo}` : "请选择上方清分单查看费用规则和分账明细" }}</p>
        </div>
      </div>
      <div v-if="selectedOrder" class="detail-summary-grid">
        <div class="summary-card">
          <strong>支付与订单</strong>
          <span>{{ selectedOrder.paymentOrderId }} / {{ selectedOrder.orderNo }}</span>
        </div>
        <div class="summary-card">
          <strong>清分状态</strong>
          <span>{{ selectedOrder.clearingStatus }} / {{ selectedOrder.ruleNo }}</span>
        </div>
        <div class="summary-card">
          <strong>金额摘要</strong>
          <span>订单 {{ selectedOrder.orderAmount }}，平台 {{ selectedOrder.platformAmount }}，服务者 {{ selectedOrder.workerAmount }}</span>
        </div>
      </div>
      <div v-if="!selectedOrder" class="state-box">从上方清分结果列表选择一笔清分单后，可查看费用规则快照和分账明细。</div>
      <div v-else-if="detailMessage" class="state-box">{{ detailMessage }}</div>
      <div v-else-if="detailLoading" class="state-box">清分详情加载中...</div>
      <template v-else-if="selectedDetail">
        <div class="detail-columns">
          <div class="detail-panel">
            <h4>费用规则快照</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>费用名称</th>
                    <th>费用类型</th>
                    <th>模式</th>
                    <th>费率</th>
                    <th>固定金额</th>
                    <th>承担方</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in detailFeeRules" :key="row.feeRuleNo">
                    <td>{{ row.feeName }}</td>
                    <td>{{ row.feeType }}</td>
                    <td>{{ row.feeMode }}</td>
                    <td>{{ row.feeRate }}</td>
                    <td>{{ row.fixedAmount }}</td>
                    <td>{{ row.feeBearer }}</td>
                  </tr>
                  <tr v-if="detailFeeRules.length === 0">
                    <td colspan="6" class="empty-cell">当前清分单暂无费用规则快照。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="detail-panel">
            <h4>分账明细</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>分账类型</th>
                    <th>对象编号</th>
                    <th>对象名称</th>
                    <th>金额</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in detailShareItems" :key="row.shareItemNo">
                    <td>{{ row.shareType }}</td>
                    <td>{{ row.shareTargetNo }}</td>
                    <td>{{ row.shareTargetName }}</td>
                    <td class="amount">{{ row.shareAmount }}</td>
                    <td><span class="badge" :class="row.statusType">{{ row.shareStatus }}</span></td>
                  </tr>
                  <tr v-if="detailShareItems.length === 0">
                    <td colspan="5" class="empty-cell">当前清分单暂无分账明细。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.summary-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  margin-bottom: 16px;
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  color: #1e3a8a;
}

.topbar-inner {
  margin-bottom: 12px;
}

.topbar-inner h3 {
  margin: 0;
  font-size: 20px;
}

.detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.detail-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-panel h4 {
  margin: 0 0 12px;
  font-size: 16px;
}

.button-inline {
  padding: 8px 12px;
}

.amount {
  color: #b45309;
  font-weight: 700;
}

.deduct {
  color: #b91c1c;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}
</style>
