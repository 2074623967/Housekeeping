const {
  SCENES,
  createOrderNo,
  normalizeAmount,
  resolveLinkedScene,
  resolveScene
} = require("../../utils/payment");
const { paymentApi } = require("../../utils/request");

function buildForm(scene) {
  return {
    customerName: scene.defaultCustomerName,
    amount: scene.defaultAmount,
    orderNo: "",
    remark: ""
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
    const sceneKey = options && Object.prototype.hasOwnProperty.call(options, "scene")
      ? options.scene
      : null;
    const payScene = options && Object.prototype.hasOwnProperty.call(options, "payScene")
      ? options.payScene
      : null;
    this.applyScene(sceneKey, payScene);
  },

  applyScene(sceneKey, payScene = null) {
    if (this.data.submitting) {
      return;
    }
    const scene = resolveLinkedScene(sceneKey, payScene);
    this.setData({
      activeSceneKey: scene.key,
      activeScene: scene,
      form: buildForm(scene),
      errorMessage: scene.payScene ? "" : "当前小程序暂不支持该业务场景，请先选择上方已支持的场景。"
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
    if (this.data.submitting || !this.data.activeScene.payScene) {
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
    if (!this.data.activeScene.payScene) {
      this.setData({ errorMessage: "请先选择小程序已支持的业务场景。" });
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
        cashierTitle: `${this.data.activeScene.title}收银台`,
        remark: this.data.form.remark.trim()
      });
      if (!prepay || !prepay.prepayOrderNo) {
        throw new Error("预付单响应为空或缺少预付单号");
      }
      const prepayOrderNo = prepay.prepayOrderNo.trim();
      wx.navigateTo({
        url: `/pages/cashier/index?prepayOrderNo=${encodeURIComponent(prepayOrderNo)}`
          + `&scene=${encodeURIComponent(this.data.activeScene.key)}`
      });
    } catch (error) {
      this.setData({ errorMessage: error.message || "预付单创建失败" });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
