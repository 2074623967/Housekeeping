<script setup>
const accounts = [
  {
    walletAccountNo: "WA-USER-001",
    ownerName: "王阿姨",
    ownerType: "USER",
    accountScene: "USER_STORE",
    accountStatus: "ACTIVE",
    availableBalance: "240.00",
    frozenBalance: "20.00",
    pendingInBalance: "8.00",
    pendingOutBalance: "0.00",
    totalBalance: "268.00"
  },
  {
    walletAccountNo: "WA-WORKER-001",
    ownerName: "李师傅",
    ownerType: "WORKER",
    accountScene: "WORKER_INCOME",
    accountStatus: "FROZEN",
    availableBalance: "1000.00",
    frozenBalance: "200.00",
    pendingInBalance: "80.00",
    pendingOutBalance: "20.00",
    totalBalance: "1260.00"
  }
];

const recentFlows = [
  { flowNo: "WF-0003", flowType: "FREEZE", sourceSystem: "wallet-withdraw", sourceBizNo: "WD-LOCK-001", amount: "200.00" },
  { flowNo: "WF-0002", flowType: "PENDING_IN", sourceSystem: "payment-core", sourceBizNo: "PAY-RECHARGE-001", amount: "8.00" },
  { flowNo: "WF-0001", flowType: "OPEN_ACCOUNT", sourceSystem: "wallet-account", sourceBizNo: "OPEN-WA-USER-001", amount: "0.00" }
];
</script>

<template>
  <section class="page">
    <div class="layout">
      <div class="panel">
        <h2>钱包账户列表</h2>
        <p class="muted">本轮先把查询、详情和状态流转入口占住，真实联调下一轮接入。</p>
        <div class="toolbar">
          <input placeholder="账户号 / 主体名称" />
          <select>
            <option>全部主体</option>
            <option>USER</option>
            <option>WORKER</option>
          </select>
          <select>
            <option>全部状态</option>
            <option>ACTIVE</option>
            <option>FROZEN</option>
            <option>CLOSED</option>
          </select>
          <button class="button">模拟开户</button>
          <button class="button button--light">状态流转</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>账户号</th>
                <th>主体</th>
                <th>场景</th>
                <th>状态</th>
                <th>可用余额</th>
                <th>冻结余额</th>
                <th>在途入账</th>
                <th>在途出账</th>
                <th>总余额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="account in accounts" :key="account.walletAccountNo">
                <td>{{ account.walletAccountNo }}</td>
                <td>{{ account.ownerName }} / {{ account.ownerType }}</td>
                <td>{{ account.accountScene }}</td>
                <td>
                  <span class="badge" :class="account.accountStatus === 'ACTIVE' ? 'badge--ok' : 'badge--warn'">
                    {{ account.accountStatus }}
                  </span>
                </td>
                <td>{{ account.availableBalance }}</td>
                <td>{{ account.frozenBalance }}</td>
                <td>{{ account.pendingInBalance }}</td>
                <td>{{ account.pendingOutBalance }}</td>
                <td>{{ account.totalBalance }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="detail-grid">
        <div class="panel">
          <h3>账户详情骨架</h3>
          <div class="detail-card">
            <div class="detail-label">主体与账户</div>
            <div class="detail-value">王阿姨 / WA-USER-001</div>
          </div>
          <div class="kpi-grid">
            <div class="detail-card">
              <div class="detail-label">可用余额</div>
              <div class="detail-value">240.00</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">冻结余额</div>
              <div class="detail-value">20.00</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">在途入账</div>
              <div class="detail-value">8.00</div>
            </div>
            <div class="detail-card">
              <div class="detail-label">在途出账</div>
              <div class="detail-value">0.00</div>
            </div>
          </div>
        </div>

        <div class="panel">
          <h3>最近流水</h3>
          <div class="detail-grid">
            <div v-for="flow in recentFlows" :key="flow.flowNo" class="detail-card">
              <div class="detail-label">{{ flow.flowType }} / {{ flow.flowNo }}</div>
              <div class="detail-value">{{ flow.amount }}</div>
              <div class="muted">{{ flow.sourceSystem }} · {{ flow.sourceBizNo }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
