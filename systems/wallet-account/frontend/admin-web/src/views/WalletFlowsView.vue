<script setup>
const flows = [
  {
    flowNo: "WF-0003",
    walletAccountNo: "WA-WORKER-001",
    flowType: "FREEZE",
    sourceSystem: "wallet-withdraw",
    sourceBizNo: "WD-LOCK-001",
    beforeAvailableBalance: "1200.00",
    afterAvailableBalance: "1000.00",
    operatorName: "finance_bot"
  },
  {
    flowNo: "WF-0002",
    walletAccountNo: "WA-USER-001",
    flowType: "PENDING_IN",
    sourceSystem: "payment-core",
    sourceBizNo: "PAY-RECHARGE-001",
    beforeAvailableBalance: "232.00",
    afterAvailableBalance: "240.00",
    operatorName: "system"
  }
];
</script>

<template>
  <section class="page">
    <div class="panel">
      <h2>钱包流水</h2>
      <p class="muted">对应冻结版里的“流水分页查询 + 来源系统/来源单号筛选”场景。</p>
      <div class="toolbar">
        <input placeholder="钱包账户号" />
        <input placeholder="来源系统" />
        <input placeholder="来源业务单号" />
        <button class="button">筛选</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>流水号</th>
              <th>账户号</th>
              <th>流水类型</th>
              <th>来源系统</th>
              <th>来源业务单号</th>
              <th>变更前可用余额</th>
              <th>变更后可用余额</th>
              <th>操作人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="flow in flows" :key="flow.flowNo">
              <td>{{ flow.flowNo }}</td>
              <td>{{ flow.walletAccountNo }}</td>
              <td>{{ flow.flowType }}</td>
              <td>{{ flow.sourceSystem }}</td>
              <td>{{ flow.sourceBizNo }}</td>
              <td>{{ flow.beforeAvailableBalance }}</td>
              <td>{{ flow.afterAvailableBalance }}</td>
              <td>{{ flow.operatorName }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
