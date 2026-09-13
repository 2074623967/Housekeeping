const {
  SCENES,
  createOrderNo,
  normalizeAmount,
  resolveScene
} = require("../../utils/payment");
const { paymentApi } = require("../../utils/request");

function buildForm(scene) {
  return {
    customerName: scene.defaultCustomerName,
    amount: scene.defaultAmount,
    orderNo: ""
  };
}

Page({
  data: {
    scenes: Object.keys(SCENES).map((key) => SCENES[key]),
    activeSceneKey: "balance-pay",
    activeScene: resolveScene("balance-pay"),
    form: buildForm(resolveScene("balance-pay")),
    submitting: false,
    errorMessage: ""
  },

  onLoad(options) {
    this.applyScene(options && options.scene);
  },

  applyScene(sceneKey) {
    if (this.data.submitting) {
      return;
    }
    const scene = resolveScene(sceneKey);
    this.setData({
      activeSceneKey: scene.key,
      activeScene: scene,
      form: buildForm(scene),
      errorMessage: ""
    });
  },

  switchScene(event) {
    this.applyScene(event.currentTarget.dataset.scene);
  },

  updateField(event) {
    const field = event.currentTarget.dataset.field;
    if (!Object.prototype.hasOwnProperty.call(this.data.form, field)) {
      return;
    }
    this.setData({ [`form.${field}`]: event.detail.value });
  },

  resetForm() {
    if (this.data.submitting) {
      return;
    }
    this.setData({
      form: buildForm(this.data.activeScene),
      errorMessage: ""
    });
  },

  async createPrepay() {
    if (this.data.submitting) {
      return;
    }
    const customerName = this.data.form.customerName.trim();
    const amount = normalizeAmount(this.data.form.amount);
    if (!customerName) {
      this.setData({ errorMessage: "请先填写客户名称。" });
      return;
    }
    if (!amount) {
      this.setData({ errorMessage: "请输入大于 0、最多 16 位整数和 2 位小数的金额。" });
      return;
    }

    this.setData({ submitting: true, errorMessage: "" });
    try {
      const orderNo = this.data.form.orderNo.trim() || createOrderNo(this.data.activeScene);
      const prepay = await paymentApi.prepay({
        orderNo,
        payScene: this.data.activeScene.payScene,
        customerName,
        amount,
        cashierTitle: `${this.data.activeScene.title}收银台`
      });
      if (!prepay || !prepay.prepayOrderNo) {
        throw new Error("预付单响应为空或缺少预付单号");
      }
      wx.navigateTo({
        url: `/pages/cashier/index?prepayOrderNo=${encodeURIComponent(prepay.prepayOrderNo)}`
          + `&scene=${encodeURIComponent(this.data.activeScene.key)}`
      });
    } catch (error) {
      this.setData({ errorMessage: error.message || "预付单创建失败" });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
