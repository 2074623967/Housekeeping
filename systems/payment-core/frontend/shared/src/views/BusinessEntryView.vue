<script setup>
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { paymentApi } from "../../../app-web/src/api/client";

const props = defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  },
  sceneType: {
    type: String,
    default: "balance-pay"
  }
});

const router = useRouter();
const submitting = ref(false);
const message = ref("");
const form = ref({
  customerName: "",
  amount: "",
  orderNo: "",
  remark: ""
});

const TERMINAL_META = {
  app: {
    title: "家政 App 支付业务入口",
    hint: "适用于用户在 App 内发起充值、补款、转账和提现申请后的统一支付拉起。",
    sourceAppId: "housekeeping-app-web",
    accessToken: "token-housekeeping-app-web"
  },
  h5: {
    title: "家政 H5 支付业务入口",
    hint: "适用于短信触达、活动页和轻量预约页中的支付业务入口。",
    sourceAppId: "housekeeping-h5-web",
    accessToken: "token-housekeeping-h5-web"
  },
  pc: {
    title: "家政 PC 支付业务入口",
    hint: "适用于运营代客支付、企业客户付款和桌面端业务办理场景。",
    sourceAppId: "housekeeping-pc-web",
    accessToken: "token-housekeeping-pc-web"
  }
};

const SCENE_META = {
  recharge: {
    tabLabel: "充值",
    title: "钱包充值",
    description: "面向用户账户充值、预存款补充和会员资金预入账。",
    amountLabel: "充值金额",
    paySceneByTerminal: {
      app: "WALLET_RECHARGE_APP",
      h5: "WALLET_RECHARGE_H5",
      pc: "WALLET_RECHARGE_PC"
    },
    cashierTitle: "钱包充值收银台",
    orderPrefix: "WALLET-RECHARGE",
    defaultCustomerName: "张女士",
    defaultAmount: "99.00",
    placeholders: {
      orderNo: "可留空，系统自动生成充值单号",
      remark: "例如：会员储值、活动充值、押金补缴"
    }
  },
  withdraw: {
    tabLabel: "提现",
    title: "账户提现补款",
    description: "用于提现手续费补缴、失败重试补款和提现链路中的再次扣费。",
    amountLabel: "补款金额",
    paySceneByTerminal: {
      app: "WITHDRAW_REPAY_APP",
      h5: "WITHDRAW_REPAY_H5",
      pc: "WITHDRAW_REPAY_PC"
    },
    cashierTitle: "提现补款收银台",
    orderPrefix: "WITHDRAW-REPAY",
    defaultCustomerName: "王先生",
    defaultAmount: "25.00",
    placeholders: {
      orderNo: "可留空，系统自动生成提现补款单号",
      remark: "例如：提现失败重试手续费、银行卡校验补扣"
    }
  },
  transfer: {
    tabLabel: "转账",
    title: "转账补款",
    description: "用于商家钱包转账、服务者垫资补款和内部转账类业务支付。",
    amountLabel: "转账金额",
    paySceneByTerminal: {
      app: "ACCOUNT_TRANSFER_APP",
      h5: "ACCOUNT_TRANSFER_H5",
      pc: "ACCOUNT_TRANSFER_PC"
    },
    cashierTitle: "转账补款收银台",
    orderPrefix: "ACCOUNT-TRANSFER",
    defaultCustomerName: "企业客户-晨星科技",
    defaultAmount: "300.00",
    placeholders: {
      orderNo: "可留空，系统自动生成转账业务单号",
      remark: "例如：商家转账、服务者补贴、内部资金划转"
    }
  },
  "balance-pay": {
    tabLabel: "余额支付",
    title: "余额支付补款",
    description: "用于订单尾款补缴、账户余额不足兜底和履约前二次支付。",
    amountLabel: "支付金额",
    paySceneByTerminal: {
      app: "BALANCE_PAY_APP",
      h5: "BALANCE_PAY_H5",
      pc: "BALANCE_PAY_PC"
    },
    cashierTitle: "余额支付收银台",
    orderPrefix: "BALANCE-PAY",
    defaultCustomerName: "张女士",
    defaultAmount: "68.00",
    placeholders: {
      orderNo: "可留空，系统自动生成余额支付单号",
      remark: "例如：订单尾款、超时补缴、二次上门补款"
    }
  }
};

const terminalMeta = computed(() => TERMINAL_META[props.terminalVariant] || TERMINAL_META.app);
const sceneMeta = computed(() => SCENE_META[props.sceneType] || SCENE_META["balance-pay"]);
const tabs = computed(() => Object.entries(SCENE_META).map(([key, value]) => ({
  key,
  label: value.tabLabel,
  path: `/${key}`
})));
const entryChecklist = computed(() => [
  `业务类型：${sceneMeta.value.title}`,
  `终端来源：${terminalMeta.value.sourceAppId}`,
  `支付场景：${sceneMeta.value.paySceneByTerminal[props.terminalVariant]}`,
  "提交后会直接进入统一收银台并复用支付主链路风控与幂等控制"
]);

function resetForm() {
  form.value = {
    customerName: sceneMeta.value.defaultCustomerName,
    amount: sceneMeta.value.defaultAmount,
    orderNo: "",
    remark: ""
  };
  message.value = "";
}

function buildOrderNo() {
  return `${sceneMeta.value.orderPrefix}-${props.terminalVariant.toUpperCase()}-${Date.now()}`;
}

watch(() => props.sceneType, resetForm, { immediate: true });

async function createPrepay() {
  if (!form.value.customerName.trim()) {
    message.value = "请先填写客户名称。";
    return;
  }
  if (!Number(form.value.amount) || Number(form.value.amount) <= 0) {
    message.value = "请输入大于 0 的金额。";
    return;
  }
  submitting.value = true;
  message.value = "";
  try {
    const orderNo = form.value.orderNo.trim() || buildOrderNo();
    const prepay = await paymentApi.prepay({
      orderNo,
      payScene: sceneMeta.value.paySceneByTerminal[props.terminalVariant],
      customerName: form.value.customerName.trim(),
      amount: Number(form.value.amount),
      cashierTitle: sceneMeta.value.cashierTitle
    });
    router.push({
      path: `/cashier/${prepay.prepayOrderNo}`,
      query: {
        accessToken: terminalMeta.value.accessToken,
        sourceAppId: terminalMeta.value.sourceAppId,
        bizType: props.sceneType
      }
    });
  } catch (error) {
    message.value = error.message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="terminal-page">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">Business Entry</div>
        <h1>{{ terminalMeta.title }}</h1>
        <p>{{ terminalMeta.hint }}</p>
      </div>
      <div class="hero-amount-card">
        <div class="mini-label">{{ sceneMeta.title }}</div>
        <div class="hero-amount">¥{{ form.amount || sceneMeta.defaultAmount }}</div>
        <div class="hero-status-row">
          <span class="status-pill status-info">{{ sceneMeta.tabLabel }}</span>
          <span class="status-pill status-success">{{ props.terminalVariant.toUpperCase() }}</span>
        </div>
      </div>
    </section>

    <section class="terminal-card entry-shell">
      <div class="entry-tabs">
        <router-link
          v-for="tab in tabs"
          :key="tab.key"
          :to="tab.path"
          class="entry-tab"
          :class="{ active: tab.key === sceneType }"
        >
          {{ tab.label }}
        </router-link>
      </div>

      <div class="entry-grid">
        <div class="entry-form">
          <div class="section-heading">
            <div>
              <h3>{{ sceneMeta.title }}</h3>
              <p>{{ sceneMeta.description }}</p>
            </div>
            <span class="support-tag">统一预付单入口</span>
          </div>

          <div class="entry-field-grid">
            <label class="entry-field">
              <span>客户名称</span>
              <input v-model="form.customerName" placeholder="请输入客户名称" />
            </label>
            <label class="entry-field">
              <span>{{ sceneMeta.amountLabel }}</span>
              <input v-model="form.amount" placeholder="请输入金额" />
            </label>
            <label class="entry-field entry-field-full">
              <span>业务单号</span>
              <input v-model="form.orderNo" :placeholder="sceneMeta.placeholders.orderNo" />
            </label>
            <label class="entry-field entry-field-full">
              <span>业务说明</span>
              <textarea v-model="form.remark" rows="4" :placeholder="sceneMeta.placeholders.remark" />
            </label>
          </div>

          <div v-if="message" class="inline-warning">{{ message }}</div>

          <div class="action-row">
            <button class="primary-button" :disabled="submitting" @click="createPrepay">
              {{ submitting ? "预付单创建中..." : "创建预付单并进入收银台" }}
            </button>
            <button class="ghost-button" :disabled="submitting" @click="resetForm">重置表单</button>
          </div>
        </div>

        <div class="entry-side">
          <div class="entry-aside-card terminal-card">
            <div class="section-heading">
              <div>
                <h3>接入检查清单</h3>
                <p>确保预付单创建前的终端、场景和支付链路参数已经准备完成。</p>
              </div>
            </div>
            <ul class="entry-checklist">
              <li v-for="item in entryChecklist" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
