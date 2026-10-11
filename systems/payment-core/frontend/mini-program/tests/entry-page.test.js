const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const test = require("node:test");

const { paymentApi } = require("../utils/request");

let pageDefinition;
const originalPage = global.Page;
global.Page = (definition) => {
  pageDefinition = definition;
};
require("../pages/entry/index");
global.Page = originalPage;

function createPage() {
  return {
    ...pageDefinition,
    data: {
      ...pageDefinition.data,
      form: { ...pageDefinition.data.form }
    },
    setData(update) {
      for (const [key, value] of Object.entries(update)) {
        if (key.startsWith("form.")) {
          this.data.form[key.slice("form.".length)] = value;
        } else {
          this.data[key] = value;
        }
      }
    }
  };
}

test("trims the prepay order number before navigating to the cashier", async () => {
  const previousNavigateTo = global.wx && global.wx.navigateTo;
  let navigationUrl = "";
  global.wx = {
    navigateTo(options) {
      navigationUrl = options.url;
    }
  };
  paymentApi.prepay = () => Promise.resolve({ prepayOrderNo: " PRE-001 " });
  const page = createPage();

  await page.createPrepay();

  assert.equal(
    navigationUrl,
    "/pages/cashier/index?prepayOrderNo=PRE-001&scene=balance-pay"
  );
  assert.equal(page.data.errorMessage, "");
  global.wx.navigateTo = previousNavigateTo;
});

test("does not create a balance prepay from an unsupported entry link", async () => {
  const source = readFileSync(join(__dirname, "../pages/entry/index.wxml"), "utf8");
  assert.match(source, /wx:if="\{\{!activeScene\.payScene\}\}"/);
  let requestCount = 0;
  paymentApi.prepay = () => {
    requestCount += 1;
    return Promise.resolve({ prepayOrderNo: "PRE-001" });
  };
  for (const options of [
    { scene: "unknown" },
    { scene: "__proto__" },
    { scene: "" },
    { payScene: "HOME_CLEAN_MINI_PROGRAM" },
    { scene: "balance-pay", payScene: "WALLET_RECHARGE_MINI_PROGRAM" }
  ]) {
    const page = createPage();
    page.onLoad(options);
    assert.equal(page.data.activeSceneKey, "");
    assert.match(page.data.errorMessage, /不支持该业务场景/);
    await page.createPrepay();
    assert.match(page.data.errorMessage, /先选择/);
    page.switchScene({ currentTarget: { dataset: { scene: "recharge" } } });
    assert.equal(page.data.activeScene.payScene, "WALLET_RECHARGE_MINI_PROGRAM");
    assert.equal(page.data.errorMessage, "");
  }
  assert.equal(requestCount, 0);
});
