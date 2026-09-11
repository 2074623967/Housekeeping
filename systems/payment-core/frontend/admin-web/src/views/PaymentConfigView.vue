<script setup>
import { computed, onMounted, ref } from "vue";
import { paymentConfigApi } from "../api/client";
import { normalizeNonNegativePaymentAmountInput } from "../utils/paymentAmount";

const channels = ref([]);
const routeRules = ref([]);
const protocols = ref([]);
const protocolTypeOptions = ref([]);
const returnCodeMappings = ref([]);
const gateways = ref([]);
const gatewayReviews = ref([]);
const isGatewayReviewLoading = ref(false);
const gatewayReviewError = ref("");
const gatewayActionRequester = ref("");
const gatewayActionReason = ref("");
const gatewayReviewer = ref("");
const gatewayReviewComment = ref("");
const gatewayVersionCode = ref("");
const gatewayVersions = ref([]);
const isGatewayVersionLoading = ref(false);
const gatewayVersionError = ref("");
let gatewayVersionRequestId = 0;
const editingGatewayCode = ref("");
const gatewayForm = ref(createGatewayForm());
const controlPolicies = ref([]);
const controlPolicyReviews = ref([]);
const isControlPolicyReviewLoading = ref(false);
const controlPolicyReviewError = ref("");
const controlPolicyActionRequester = ref("");
const controlPolicyActionReason = ref("");
const controlPolicyReviewer = ref("");
const controlPolicyReviewComment = ref("");
const controlPolicyVersionSourceAppId = ref("");
const controlPolicyVersions = ref([]);
const isControlPolicyVersionLoading = ref(false);
const controlPolicyVersionError = ref("");
let controlPolicyVersionRequestId = 0;
const editingControlPolicyId = ref("");
const controlPolicyForm = ref(createControlPolicyForm());
const alertProviders = ref([]);
const alertProviderReviews = ref([]);
const isAlertProviderReviewLoading = ref(false);
const alertProviderReviewError = ref("");
const alertProviderActionRequester = ref("");
const alertProviderActionReason = ref("");
const alertProviderReviewer = ref("");
const alertProviderReviewComment = ref("");
const editingAlertProviderCode = ref("");
const issueDutyRosters = ref([]);
const issueDutyRosterReviews = ref([]);
const isIssueDutyRosterReviewLoading = ref(false);
const issueDutyRosterReviewError = ref("");
const issueDutyRosterActionRequester = ref("");
const issueDutyRosterActionReason = ref("");
const issueDutyRosterReviewer = ref("");
const issueDutyRosterReviewComment = ref("");
const isLoading = ref(true);
const hasLoadedOverview = ref(false);
const overviewErrorMessage = ref("");
const actionMessage = ref("");
const actionMessagePresentation = computed(() => {
  const message = actionMessage.value;
  if (message.includes("失败") || message.includes("未通过") || message.includes("不合法")) {
    return { className: "error-banner", role: "alert" };
  }
  if (message.startsWith("正在编辑") || message.startsWith("已读取") || message.includes("项待处理")) {
    return { className: "notice-banner", role: "status" };
  }
  return { className: "success-banner", role: "status" };
});
const activeConfigCode = ref("");
const channelReviews = ref([]);
const isChannelReviewLoading = ref(false);
const channelReviewError = ref("");
const channelActionRequester = ref("");
const channelActionReason = ref("");
const channelReviewer = ref("");
const channelReviewComment = ref("");
const channelVersionCode = ref("");
const channelVersions = ref([]);
const isChannelVersionLoading = ref(false);
const channelVersionError = ref("");
let channelVersionRequestId = 0;
const editingChannelCode = ref("");
const channelForm = ref(createChannelForm());
const editingRouteRuleCode = ref("");
const versionRuleCode = ref("");
const routeRuleVersions = ref([]);
const isVersionLoading = ref(false);
const routeRuleVersionError = ref("");
let routeRuleVersionRequestId = 0;
const routeRuleConflicts = ref([]);
const isConflictLoading = ref(false);
const hasCheckedRouteRuleConflicts = ref(false);
const routeRuleReviews = ref([]);
const isReviewLoading = ref(false);
const reviewErrorMessage = ref("");
const routeRuleActionRequester = ref("");
const routeRuleActionReason = ref("");
const reviewer = ref("");
const reviewComment = ref("");
const editingProtocolCode = ref("");
const protocolVersionCode = ref("");
const protocolVersions = ref([]);
const isProtocolVersionLoading = ref(false);
const protocolVersionError = ref("");
let protocolVersionRequestId = 0;
const protocolReviews = ref([]);
const isProtocolReviewLoading = ref(false);
const protocolReviewErrorMessage = ref("");
const protocolActionRequester = ref("");
const protocolActionReason = ref("");
const protocolReviewer = ref("");
const protocolReviewComment = ref("");
const editingRosterCode = ref("");
const returnCodeImportFileName = ref("");
const returnCodeImportFileInput = ref(null);
const returnCodeImportCsv = ref("");
const returnCodeImportRequester = ref("");
const returnCodeImportReason = ref("");
const returnCodeImportPreview = ref(null);
const isReturnCodeImportPreviewing = ref(false);
const isReturnCodeImportSubmitting = ref(false);
const returnCodeImportReviews = ref([]);
const isReturnCodeImportReviewLoading = ref(false);
const returnCodeImportReviewError = ref("");
const returnCodeImportReviewer = ref("");
const returnCodeImportReviewComment = ref("");
const returnCodeReviews = ref([]);
const isReturnCodeReviewLoading = ref(false);
const returnCodeReviewError = ref("");
const returnCodeActionRequester = ref("");
const returnCodeActionReason = ref("");
const returnCodeReviewer = ref("");
const returnCodeReviewComment = ref("");
const protocolForm = ref(createProtocolForm());
const routeRuleForm = ref(createRouteRuleForm());
const rosterForm = ref(createRosterForm());
const alertProviderForm = ref(createAlertProviderForm());

const governanceMetrics = computed(() => ({
  enabledChannelCount: channels.value.filter((item) => item.status === "ENABLED").length,
  enabledRouteRuleCount: routeRules.value.filter((item) => item.status === "ENABLED").length,
  enabledProtocolCount: protocols.value.filter((item) => item.status === "ENABLED").length,
  enabledGatewayCount: gateways.value.filter((item) => item.status === "ENABLED").length,
  enabledControlPolicyCount: controlPolicies.value.filter((item) => item.status === "ENABLED").length,
  warningControlPolicyCount: controlPolicies.value.filter((item) => item.selfCheckStatus === "WARN").length,
  failedControlPolicyCount: controlPolicies.value.filter((item) => item.selfCheckStatus === "FAIL").length,
  enabledAlertProviderCount: alertProviders.value.filter((item) => item.status === "ENABLED").length,
  enabledIssueDutyRosterCount: issueDutyRosters.value.filter((item) => item.status === "ENABLED").length,
  archivedReturnCodeCount: returnCodeMappings.value.filter((item) => item.archiveStatus === "ARCHIVED").length
}));

function createProtocolForm() {
  return {
    protocolCode: "",
    protocolName: "",
    protocolType: "",
    protocolTypeName: "",
    templateCode: "",
    templateName: "",
    templateVersion: "",
    signMode: "",
    signElementSpec: "",
    eSignatureProvider: "",
    sceneScope: "",
    channelScope: "",
    merchantAckRequired: "需要",
    riskControlTag: "",
    protocolBody: "",
    priority: 99,
    enabled: true,
    changeReason: "",
    requester: ""
  };
}

function createChannelForm() {
  return {
    channelCode: "",
    channelName: "",
    paymentMethod: "",
    merchantNo: "",
    merchantAppId: "",
    callbackSecret: "",
    callbackSignAlgorithm: "HMAC-SHA256",
    callbackPublicKey: "",
    callbackNotifyUrl: "",
    certificateProfile: "",
    notifySignWindowSec: 300,
    refundWindowDays: 180,
    riskControlTag: "",
    sceneScope: "",
    enabled: true,
    dailyLimit: 0,
    priority: 0,
    routeWeight: 100,
    changeReason: "",
    requester: ""
  };
}

function createGatewayForm() {
  return {
    gatewayCode: "",
    gatewayName: "",
    accessMode: "直连",
    channelScope: "",
    environmentScope: "生产+预发",
    apiBaseUrl: "",
    protocolType: "HTTP+JSON",
    signAlgorithm: "HMAC-SHA256",
    certificateAlias: "",
    certificateStatus: "VALID",
    releaseStage: "灰度",
    grayStrategy: "",
    callbackWhitelist: "",
    adapterRegistry: "",
    timeoutMs: 3000,
    retryPolicy: "失败重试1次+超时查单",
    enabled: true,
    priority: 0,
    changeReason: "",
    requester: ""
  };
}

function createControlPolicyForm() {
  return {
    sourceAppId: "",
    sourceAppName: "",
    allowedPaymentMethods: "",
    allowedChannelCodes: "",
    allowedMerchantNos: "",
    minuteSubmitLimit: 60,
    interfaceMinuteSubmitLimit: 30,
    tokenAuthRequired: true,
    accessTokenValue: "",
    strictMode: true,
    enabled: true,
    changeReason: "",
    requester: ""
  };
}

function createRouteRuleForm() {
  return {
    ruleCode: "",
    ruleName: "",
    matchScene: "",
    matchExpression: "",
    targetChannelCode: "",
    fallbackChannelCode: "",
    rolloutPercentage: 100,
    rolloutSalt: "stable-v1",
    priority: 99,
    enabled: true,
    changeReason: "",
    requester: "",
    samplePaymentMethod: "微信支付",
    sampleRequestedChannelCode: "",
    samplePayScene: "HOME_CLEAN",
    sampleTerminal: "H5",
    sampleAmount: 100,
    sampleCustomerName: "个人客户"
  };
}

function createRosterForm() {
  return {
    rosterCode: "",
    issueType: "",
    severity: "P1",
    responsibilityGroup: "",
    receiver: "",
    notifyChannels: "IN_APP,IM",
    escalationLevel: "L1",
    escalationReceiver: "",
    escalationPolicy: "",
    escalationTimeoutMinutes: 30,
    scheduleTag: "",
    effectiveStartHour: 0,
    effectiveEndHour: 23,
    weekdayScope: "1,2,3,4,5",
    holidayStrategy: "WORKDAY_ONLY",
    enabled: true,
    requester: "",
    changeReason: ""
  };
}

function createAlertProviderForm() {
  return {
    providerCode: "",
    providerName: "",
    channelCode: "IM",
    endpointAlias: "",
    templateCode: "",
    templateBody: "",
    routeRule: "",
    routePriority: 100,
    retryPolicy: "",
    rateLimitPolicy: "",
    enabled: true,
    requester: "",
    changeReason: ""
  };
}

function formatWeekdayScopeLabel(weekdayScope) {
  const weekdayLabelMap = {
    "1": "周一",
    "2": "周二",
    "3": "周三",
    "4": "周四",
    "5": "周五",
    "6": "周六",
    "7": "周日"
  };
  return (weekdayScope || "")
    .split(",")
    .map((item) => weekdayLabelMap[item.trim()])
    .filter(Boolean)
    .join(" / ");
}

function formatHolidayStrategyLabel(holidayStrategy) {
  if (holidayStrategy === "WORKDAY_ONLY") {
    return "仅工作日";
  }
  if (holidayStrategy === "NON_WORKDAY_ONLY") {
    return "仅非工作日";
  }
  return "全部日期";
}

async function loadOverview() {
  isLoading.value = true;
  overviewErrorMessage.value = "";
  try {
    applyOverview(await paymentConfigApi.getOverview());
    hasLoadedOverview.value = true;
  } catch (error) {
    overviewErrorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function applyOverview(overview) {
  channels.value = overview.channels || [];
  routeRules.value = overview.routeRules || [];
  protocols.value = overview.protocols || [];
  protocolTypeOptions.value = overview.protocolTypeOptions || [];
  returnCodeMappings.value = overview.returnCodeMappings || [];
  gateways.value = overview.gateways || [];
  controlPolicies.value = overview.controlPolicies || [];
  alertProviders.value = overview.alertProviders || [];
  issueDutyRosters.value = overview.issueDutyRosters || [];
  routeRuleConflicts.value = [];
  hasCheckedRouteRuleConflicts.value = false;
}

async function submitChannelStatusReview(channel) {
  if (!channelActionRequester.value || !channelActionReason.value) {
    actionMessage.value = "渠道启停申请失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = channel.status !== "ENABLED";
  if (!window.confirm(`确认提交渠道 ${channel.channelCode} 的${enabled ? "启用" : "停用"}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = channel.channelCode;
  actionMessage.value = "";
  try {
    channelReviews.value = await paymentConfigApi.submitChannelStatusReview({
      channelCode: channel.channelCode,
      enabled,
      requester: channelActionRequester.value,
      changeReason: channelActionReason.value
    });
    channelActionReason.value = "";
    actionMessage.value = `渠道 ${channel.channelCode} 的${enabled ? "启用" : "停用"}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `渠道启停申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateChannel() {
  editingChannelCode.value = "";
  channelForm.value = createChannelForm();
  actionMessage.value = "";
}

function startEditChannel(channel) {
  editingChannelCode.value = channel.channelCode;
  channelForm.value = {
    channelCode: channel.channelCode,
    channelName: channel.channelName,
    paymentMethod: channel.paymentMethod,
    merchantNo: channel.merchantNo,
    merchantAppId: channel.merchantAppId,
    callbackSecret: "",
    callbackSignAlgorithm: channel.callbackSignAlgorithm,
    callbackPublicKey: "",
    callbackNotifyUrl: channel.callbackNotifyUrl,
    certificateProfile: channel.certificateProfile,
    notifySignWindowSec: channel.notifySignWindowSec,
    refundWindowDays: channel.refundWindowDays,
    riskControlTag: channel.riskControlTag,
    sceneScope: channel.sceneScope,
    enabled: channel.status === "ENABLED",
    dailyLimit: channel.dailyLimitValue,
    priority: channel.priority,
    routeWeight: channel.routeWeight,
    changeReason: "",
    requester: ""
  };
  actionMessage.value = `正在编辑渠道 ${channel.channelCode}；密钥或公钥留空表示保持现值。`;
}

async function submitChannelForm() {
  if (!channelForm.value.requester || !channelForm.value.changeReason) {
    actionMessage.value = "支付渠道提交复核失败：请填写申请人声明和申请原因。";
    return;
  }
  if (!editingChannelCode.value && !channelForm.value.callbackSecret) {
    actionMessage.value = "支付渠道提交复核失败：新增渠道必须填写回调验签密钥。";
    return;
  }
  const dailyLimit = normalizeNonNegativePaymentAmountInput(channelForm.value.dailyLimit);
  if (dailyLimit === null) {
    actionMessage.value = "支付渠道提交复核失败：单日限额最多支持 16 位整数和 2 位小数。";
    return;
  }
  const channelCode = editingChannelCode.value || channelForm.value.channelCode;
  activeConfigCode.value = channelCode;
  actionMessage.value = "";
  try {
    const candidate = {
      ...channelForm.value,
      notifySignWindowSec: Number(channelForm.value.notifySignWindowSec),
      refundWindowDays: Number(channelForm.value.refundWindowDays),
      dailyLimit,
      priority: Number(channelForm.value.priority),
      routeWeight: Number(channelForm.value.routeWeight)
    };
    delete candidate.requester;
    channelReviews.value = await paymentConfigApi.submitChannelReview({
      requester: channelForm.value.requester,
      candidate
    });
    startCreateChannel();
    actionMessage.value = `支付渠道 ${channelCode} 已提交复核，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付渠道提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadChannelReviews() {
  isChannelReviewLoading.value = true;
  channelReviewError.value = "";
  try {
    channelReviews.value = await paymentConfigApi.getChannelReviews();
  } catch (error) {
    channelReviewError.value = error.message;
  } finally {
    isChannelReviewLoading.value = false;
  }
}

async function handleChannelReview(review, approved) {
  if (!channelReviewer.value) {
    actionMessage.value = "渠道复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!channelReviewComment.value) {
    actionMessage.value = `渠道复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
    ? `确认通过申请 ${review.requestNo} 并应用 ${review.candidateStatus} 状态？`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `channel-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: channelReviewer.value,
    reviewComment: channelReviewComment.value
  };
  try {
    try {
      channelReviews.value = approved
        ? await paymentConfigApi.approveChannelReview(review.requestNo, payload)
        : await paymentConfigApi.rejectChannelReview(review.requestNo, payload);
      channelReviewComment.value = "";
      actionMessage.value = approved
        ? `渠道状态申请 ${review.requestNo} 已通过并应用。`
        : `渠道状态申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `渠道复核处理失败：${error.message}`;
      return;
    }
    if (!approved) {
      return;
    }
    const refreshTasks = [loadOverview()];
    if (channelVersionCode.value) {
      refreshTasks.push(loadChannelVersions(channelVersionCode.value));
    }
    await Promise.all(refreshTasks);
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadGatewayVersions(gatewayCode) {
  const requestId = ++gatewayVersionRequestId;
  if (gatewayVersionCode.value !== gatewayCode) {
    gatewayVersions.value = [];
  }
  gatewayVersionCode.value = gatewayCode;
  isGatewayVersionLoading.value = true;
  gatewayVersionError.value = "";
  try {
    const versions = await paymentConfigApi.getGatewayVersions(gatewayCode);
    if (requestId === gatewayVersionRequestId && gatewayVersionCode.value === gatewayCode) {
      gatewayVersions.value = versions;
    }
  } catch (error) {
    if (requestId === gatewayVersionRequestId && gatewayVersionCode.value === gatewayCode) {
      gatewayVersionError.value = error.message;
    }
  } finally {
    if (requestId === gatewayVersionRequestId) {
      isGatewayVersionLoading.value = false;
    }
  }
}

function closeGatewayVersions() {
  gatewayVersionRequestId += 1;
  gatewayVersionCode.value = "";
  gatewayVersions.value = [];
  isGatewayVersionLoading.value = false;
  gatewayVersionError.value = "";
}

async function rollbackGatewayVersion(version) {
  const gatewayCode = gatewayVersionCode.value;
  if (!gatewayActionRequester.value || !gatewayActionReason.value) {
    actionMessage.value = "提交支付网关回滚复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  if (!window.confirm(`确认提交网关 ${gatewayCode} 回滚到 V${version.versionNo} 的复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = `${gatewayCode}:rollback`;
  actionMessage.value = "";
  try {
    gatewayReviews.value = await paymentConfigApi.rollbackGateway(gatewayCode, {
      versionNo: version.versionNo,
      requester: gatewayActionRequester.value,
      changeReason: gatewayActionReason.value
    });
    gatewayActionReason.value = "";
    actionMessage.value = `网关 ${gatewayCode} 回滚到 V${version.versionNo} 的申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付网关回滚申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadChannelVersions(channelCode) {
  const requestId = ++channelVersionRequestId;
  if (channelVersionCode.value !== channelCode) {
    channelVersions.value = [];
  }
  channelVersionCode.value = channelCode;
  isChannelVersionLoading.value = true;
  channelVersionError.value = "";
  try {
    const versions = await paymentConfigApi.getChannelVersions(channelCode);
    if (requestId === channelVersionRequestId && channelVersionCode.value === channelCode) {
      channelVersions.value = versions;
    }
  } catch (error) {
    if (requestId === channelVersionRequestId && channelVersionCode.value === channelCode) {
      channelVersionError.value = error.message;
    }
  } finally {
    if (requestId === channelVersionRequestId) {
      isChannelVersionLoading.value = false;
    }
  }
}

function closeChannelVersions() {
  channelVersionRequestId += 1;
  channelVersionCode.value = "";
  channelVersions.value = [];
  isChannelVersionLoading.value = false;
  channelVersionError.value = "";
}

async function rollbackChannelVersion(version) {
  const channelCode = channelVersionCode.value;
  if (!channelActionRequester.value || !channelActionReason.value) {
    actionMessage.value = "提交支付渠道回滚复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  if (!window.confirm(`确认提交渠道 ${channelCode} 回滚到 V${version.versionNo} 的复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = `${channelCode}:rollback`;
  actionMessage.value = "";
  try {
    channelReviews.value = await paymentConfigApi.rollbackChannel(channelCode, {
      versionNo: version.versionNo,
      requester: channelActionRequester.value,
      changeReason: channelActionReason.value
    });
    channelActionReason.value = "";
    actionMessage.value = `渠道 ${channelCode} 回滚到 V${version.versionNo} 的申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付渠道回滚申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function toggleRouteRule(rule) {
  if (!routeRuleActionRequester.value || !routeRuleActionReason.value) {
    actionMessage.value = "提交启停复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = rule.status !== "ENABLED";
  const actionLabel = enabled ? "启用" : "停用";
  if (!window.confirm(`确认提交路由规则 ${rule.ruleCode} 的${actionLabel}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = rule.ruleCode;
  actionMessage.value = "";
  try {
    routeRuleReviews.value = await paymentConfigApi.submitRouteRuleStatusReview({
      ruleCode: rule.ruleCode,
      enabled,
      requester: routeRuleActionRequester.value,
      changeReason: routeRuleActionReason.value
    });
    routeRuleActionReason.value = "";
    actionMessage.value = `路由规则 ${rule.ruleCode} 的${actionLabel}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `路由规则${actionLabel}申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateRouteRule() {
  editingRouteRuleCode.value = "";
  routeRuleForm.value = createRouteRuleForm();
  actionMessage.value = "";
}

function startEditRouteRule(rule) {
  editingRouteRuleCode.value = rule.ruleCode;
  routeRuleForm.value = {
    ...createRouteRuleForm(),
    ruleCode: rule.ruleCode,
    ruleName: rule.ruleName,
    matchScene: rule.matchScene,
    matchExpression: rule.matchExpression,
    targetChannelCode: rule.targetChannelCode,
    fallbackChannelCode: rule.fallbackChannelCode,
    rolloutPercentage: rule.rolloutPercentage ?? 100,
    rolloutSalt: rule.rolloutSalt || "stable-v1",
    priority: rule.priority,
    enabled: rule.status === "ENABLED",
    changeReason: ""
  };
  actionMessage.value = `正在编辑路由规则 ${rule.ruleCode}`;
}

async function validateRouteRuleForm() {
  activeConfigCode.value = editingRouteRuleCode.value || routeRuleForm.value.ruleCode || "ROUTE_RULE_VALIDATION";
  actionMessage.value = "";
  try {
    const result = await paymentConfigApi.validateRouteRule({
      matchExpression: routeRuleForm.value.matchExpression,
      paymentMethod: routeRuleForm.value.samplePaymentMethod,
      requestedChannelCode: routeRuleForm.value.sampleRequestedChannelCode,
      payScene: routeRuleForm.value.samplePayScene,
      terminal: routeRuleForm.value.sampleTerminal,
      amount: Number(routeRuleForm.value.sampleAmount || 0),
      customerName: routeRuleForm.value.sampleCustomerName
    });
    actionMessage.value = `路由表达式校验：${result.message}`;
  } catch (error) {
    actionMessage.value = `路由表达式校验失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function submitRouteRuleForm() {
  activeConfigCode.value = editingRouteRuleCode.value || routeRuleForm.value.ruleCode;
  actionMessage.value = "";
  if (!routeRuleForm.value.requester) {
    actionMessage.value = "提交复核失败：申请人声明不能为空。";
    activeConfigCode.value = "";
    return;
  }
  if (!routeRuleForm.value.changeReason) {
    actionMessage.value = "提交复核失败：申请原因不能为空。";
    activeConfigCode.value = "";
    return;
  }
  const rolloutPercentage = Number(routeRuleForm.value.rolloutPercentage);
  if (!Number.isInteger(rolloutPercentage) || rolloutPercentage < 0 || rolloutPercentage > 100) {
    actionMessage.value = "提交复核失败：灰度比例必须是 0 到 100 之间的整数。";
    activeConfigCode.value = "";
    return;
  }
  if (!routeRuleForm.value.rolloutSalt) {
    actionMessage.value = "提交复核失败：稳定分桶盐值不能为空。";
    activeConfigCode.value = "";
    return;
  }
  try {
    const savedRuleCode = editingRouteRuleCode.value || routeRuleForm.value.ruleCode;
    const payload = {
      ruleCode: routeRuleForm.value.ruleCode,
      ruleName: routeRuleForm.value.ruleName,
      matchScene: routeRuleForm.value.matchScene,
      matchExpression: routeRuleForm.value.matchExpression,
      targetChannelCode: routeRuleForm.value.targetChannelCode,
      fallbackChannelCode: routeRuleForm.value.fallbackChannelCode,
      rolloutPercentage,
      rolloutSalt: routeRuleForm.value.rolloutSalt,
      priority: routeRuleForm.value.priority === "" || routeRuleForm.value.priority == null
        ? 99
        : Number(routeRuleForm.value.priority),
      enabled: routeRuleForm.value.enabled,
      changeReason: routeRuleForm.value.changeReason
    };
    routeRuleReviews.value = await paymentConfigApi.submitRouteRuleReview({
      requester: routeRuleForm.value.requester,
      candidate: payload
    });
    startCreateRouteRule();
    actionMessage.value = `路由规则 ${savedRuleCode} 已提交复核，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `路由规则提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadRouteRuleReviews() {
  isReviewLoading.value = true;
  reviewErrorMessage.value = "";
  try {
    routeRuleReviews.value = await paymentConfigApi.getRouteRuleReviews();
  } catch (error) {
    reviewErrorMessage.value = error.message;
  } finally {
    isReviewLoading.value = false;
  }
}

function formatRouteRuleOperation(operationType) {
  const labels = {
    CREATE: "新增",
    UPDATE: "修改",
    STATUS_CHANGE: "启停",
    ROLLBACK: "回滚"
  };
  return labels[operationType] || operationType;
}

async function handleRouteRuleReview(review, approved) {
  if (!reviewer.value) {
    actionMessage.value = "复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!reviewComment.value) {
    actionMessage.value = `复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const actionLabel = approved ? "通过并发布" : "驳回";
  const confirmed = window.confirm(
    approved
      ? `确认通过申请 ${review.requestNo} 并将候选规则写入运行态？`
      : `确认驳回申请 ${review.requestNo}？运行态不会改变。`
  );
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: reviewer.value,
    reviewComment: reviewComment.value
  };
  try {
    try {
      routeRuleReviews.value = approved
        ? await paymentConfigApi.approveRouteRuleReview(review.requestNo, payload)
        : await paymentConfigApi.rejectRouteRuleReview(review.requestNo, payload);
      reviewComment.value = "";
      actionMessage.value = approved
        ? `复核申请 ${review.requestNo} 已通过，候选规则已写入运行态。`
        : `复核申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `${actionLabel}失败：${error.message}`;
      return;
    }
    if (!approved) {
      return;
    }
    const refreshTasks = [loadOverview()];
    if (versionRuleCode.value) {
      refreshTasks.push(loadRouteRuleVersions(versionRuleCode.value));
    }
    await Promise.all(refreshTasks);
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadRouteRuleVersions(ruleCode) {
  const requestId = ++routeRuleVersionRequestId;
  if (versionRuleCode.value !== ruleCode) {
    routeRuleVersions.value = [];
  }
  versionRuleCode.value = ruleCode;
  isVersionLoading.value = true;
  routeRuleVersionError.value = "";
  try {
    const versions = await paymentConfigApi.getRouteRuleVersions(ruleCode);
    if (requestId === routeRuleVersionRequestId && versionRuleCode.value === ruleCode) {
      routeRuleVersions.value = versions;
    }
  } catch (error) {
    if (requestId === routeRuleVersionRequestId && versionRuleCode.value === ruleCode) {
      routeRuleVersionError.value = error.message;
    }
  } finally {
    if (requestId === routeRuleVersionRequestId) {
      isVersionLoading.value = false;
    }
  }
}

async function detectRouteRuleConflicts() {
  isConflictLoading.value = true;
  hasCheckedRouteRuleConflicts.value = false;
  actionMessage.value = "";
  try {
    routeRuleConflicts.value = await paymentConfigApi.getRouteRuleConflicts();
    hasCheckedRouteRuleConflicts.value = true;
    actionMessage.value = routeRuleConflicts.value.length
      ? `规则冲突检测完成：发现 ${routeRuleConflicts.value.length} 项待处理结果。`
      : "规则冲突检测完成：当前启用规则未发现已覆盖类型的冲突。";
  } catch (error) {
    routeRuleConflicts.value = [];
    actionMessage.value = `规则冲突检测失败：${error.message}`;
  } finally {
    isConflictLoading.value = false;
  }
}

function closeRouteRuleVersions() {
  routeRuleVersionRequestId += 1;
  versionRuleCode.value = "";
  routeRuleVersions.value = [];
  isVersionLoading.value = false;
  routeRuleVersionError.value = "";
}

async function rollbackRouteRuleVersion(version) {
  const ruleCode = versionRuleCode.value;
  if (!routeRuleActionRequester.value || !routeRuleActionReason.value) {
    actionMessage.value = "提交回滚复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  const confirmed = window.confirm(
    `确认提交路由规则 ${ruleCode} 回滚到 V${version.versionNo} 的复核申请？运行态暂不改变。`
  );
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `${ruleCode}:rollback`;
  actionMessage.value = "";
  try {
    routeRuleReviews.value = await paymentConfigApi.submitRouteRuleRollbackReview(ruleCode, {
      versionNo: version.versionNo,
      requester: routeRuleActionRequester.value,
      changeReason: routeRuleActionReason.value
    });
    routeRuleActionReason.value = "";
    actionMessage.value = `路由规则 ${ruleCode} 回滚到 V${version.versionNo} 的申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `路由规则回滚失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function toggleProtocol(protocol) {
  if (!protocolActionRequester.value || !protocolActionReason.value) {
    actionMessage.value = "提交协议启停复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = protocol.status !== "ENABLED";
  if (!window.confirm(`确认提交支付协议 ${protocol.protocolCode} 的${enabled ? "启用" : "停用"}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = protocol.protocolCode;
  actionMessage.value = "";
  try {
    protocolReviews.value = await paymentConfigApi.toggleProtocol({
      protocolCode: protocol.protocolCode,
      enabled,
      requester: protocolActionRequester.value,
      changeReason: protocolActionReason.value
    });
    protocolActionReason.value = "";
    actionMessage.value = `支付协议 ${protocol.protocolCode} 的${enabled ? "启用" : "停用"}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付协议启停申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function toggleReturnCodeMapping(mapping) {
  if (!returnCodeActionRequester.value || !returnCodeActionReason.value) {
    actionMessage.value = "提交返回码启停复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = mapping.status !== "ENABLED";
  if (!window.confirm(`确认提交返回码 ${mapping.channelCode}/${mapping.channelReturnCode} 的${enabled ? "启用" : "停用"}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = `${mapping.channelCode}:${mapping.channelReturnCode}`;
  actionMessage.value = "";
  try {
    returnCodeReviews.value = await paymentConfigApi.submitReturnCodeStatusReview({
      channelCode: mapping.channelCode,
      channelReturnCode: mapping.channelReturnCode,
      enabled,
      requester: returnCodeActionRequester.value,
      changeReason: returnCodeActionReason.value
    });
    returnCodeActionReason.value = "";
    actionMessage.value = `返回码 ${mapping.channelCode}/${mapping.channelReturnCode} 的${enabled ? "启用" : "停用"}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `返回码启停申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadReturnCodeReviews() {
  isReturnCodeReviewLoading.value = true;
  returnCodeReviewError.value = "";
  try {
    returnCodeReviews.value = await paymentConfigApi.getReturnCodeReviews();
  } catch (error) {
    returnCodeReviewError.value = error.message;
  } finally {
    isReturnCodeReviewLoading.value = false;
  }
}

async function handleReturnCodeReview(review, approved) {
  if (!returnCodeReviewer.value) {
    actionMessage.value = "返回码复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!returnCodeReviewComment.value) {
    actionMessage.value = `返回码复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
    ? `确认通过申请 ${review.requestNo} 并应用 ${review.candidateStatus} 状态？`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `return-code-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: returnCodeReviewer.value,
    reviewComment: returnCodeReviewComment.value
  };
  try {
    try {
      returnCodeReviews.value = approved
        ? await paymentConfigApi.approveReturnCodeReview(review.requestNo, payload)
        : await paymentConfigApi.rejectReturnCodeReview(review.requestNo, payload);
      returnCodeReviewComment.value = "";
      actionMessage.value = approved
        ? `返回码状态申请 ${review.requestNo} 已通过并应用。`
        : `返回码状态申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `返回码复核处理失败：${error.message}`;
      return;
    }
    if (approved) {
      await loadOverview();
    }
  } finally {
    activeConfigCode.value = "";
  }
}

async function handleReturnCodeImportFile(event) {
  const [file] = event.target.files || [];
  returnCodeImportPreview.value = null;
  returnCodeImportCsv.value = "";
  returnCodeImportFileName.value = "";
  if (!file) {
    return;
  }
  if (!file.name.toLowerCase().endsWith(".csv")) {
    actionMessage.value = "返回码导入失败：请选择 CSV 文件。";
    event.target.value = "";
    return;
  }
  if (file.size > 512 * 1024) {
    actionMessage.value = "返回码导入失败：CSV 文件不能超过 512 KB。";
    event.target.value = "";
    return;
  }
  try {
    returnCodeImportCsv.value = await file.text();
    returnCodeImportFileName.value = file.name;
    actionMessage.value = `已读取 ${file.name}，请执行差异预检。`;
  } catch (error) {
    actionMessage.value = `返回码文件读取失败：${error.message}`;
    event.target.value = "";
  }
}

function downloadReturnCodeImportTemplate() {
  const csv = "\uFEFFchannelCode,channelReturnCode,standardizedCode,standardizedMessage,handlingSuggestion,retryable,manualInterventionRequired,mappingVersion,status,priority\n"
    + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调或主动查单,是,否,v2026.09.1,ENABLED,1\n";
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = "payment-return-code-import-template.csv";
  link.click();
  URL.revokeObjectURL(url);
}

async function previewReturnCodeImport() {
  if (!returnCodeImportCsv.value) {
    actionMessage.value = "返回码预检失败：请先选择 CSV 文件。";
    return;
  }
  isReturnCodeImportPreviewing.value = true;
  returnCodeImportPreview.value = null;
  actionMessage.value = "";
  try {
    returnCodeImportPreview.value = await paymentConfigApi.previewReturnCodeImport({
      csvContent: returnCodeImportCsv.value
    });
    actionMessage.value = returnCodeImportPreview.value.valid
      ? "返回码差异预检通过，可提交复核。"
      : `返回码差异预检未通过：${returnCodeImportPreview.value.invalidCount} 行需要修正。`;
  } catch (error) {
    actionMessage.value = `返回码差异预检失败：${error.message}`;
  } finally {
    isReturnCodeImportPreviewing.value = false;
  }
}

async function submitReturnCodeImportReview() {
  if (!returnCodeImportPreview.value?.valid) {
    actionMessage.value = "返回码复核提交失败：请先完成并通过差异预检。";
    return;
  }
  if (!returnCodeImportRequester.value || !returnCodeImportReason.value) {
    actionMessage.value = "返回码复核提交失败：申请人声明和导入原因均不能为空。";
    return;
  }
  const changedCount = returnCodeImportPreview.value.createCount + returnCodeImportPreview.value.updateCount;
  if (!window.confirm(`确认提交 ${changedCount} 条返回码配置变化进入复核？运行态暂不改变。`)) {
    return;
  }
  isReturnCodeImportSubmitting.value = true;
  actionMessage.value = "";
  try {
    returnCodeImportReviews.value = await paymentConfigApi.submitReturnCodeImportReview({
      csvContent: returnCodeImportCsv.value,
      baselineFingerprint: returnCodeImportPreview.value.baselineFingerprint,
      requester: returnCodeImportRequester.value,
      changeReason: returnCodeImportReason.value
    });
    returnCodeImportPreview.value = null;
    returnCodeImportCsv.value = "";
    returnCodeImportFileName.value = "";
    if (returnCodeImportFileInput.value) {
      returnCodeImportFileInput.value.value = "";
    }
    returnCodeImportReason.value = "";
    actionMessage.value = "返回码导入候选已提交复核，运行态尚未改变。";
  } catch (error) {
    actionMessage.value = `返回码复核提交失败：${error.message}`;
  } finally {
    isReturnCodeImportSubmitting.value = false;
  }
}

async function loadReturnCodeImportReviews() {
  isReturnCodeImportReviewLoading.value = true;
  returnCodeImportReviewError.value = "";
  try {
    returnCodeImportReviews.value = await paymentConfigApi.getReturnCodeImportReviews();
  } catch (error) {
    returnCodeImportReviewError.value = error.message;
  } finally {
    isReturnCodeImportReviewLoading.value = false;
  }
}

async function handleReturnCodeImportReview(review, approved) {
  if (!returnCodeImportReviewer.value) {
    actionMessage.value = "返回码复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!returnCodeImportReviewComment.value) {
    actionMessage.value = `返回码复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
    ? `确认通过申请 ${review.requestNo} 并应用候选返回码配置？`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `return-code-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: returnCodeImportReviewer.value,
    reviewComment: returnCodeImportReviewComment.value
  };
  try {
    try {
      returnCodeImportReviews.value = approved
        ? await paymentConfigApi.approveReturnCodeImportReview(review.requestNo, payload)
        : await paymentConfigApi.rejectReturnCodeImportReview(review.requestNo, payload);
      returnCodeImportReviewComment.value = "";
      actionMessage.value = approved
        ? `返回码导入申请 ${review.requestNo} 已通过并应用。`
        : `返回码导入申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `返回码复核处理失败：${error.message}`;
      return;
    }
    if (approved) {
      await loadOverview();
    }
  } finally {
    activeConfigCode.value = "";
  }
}

function formatReturnCodeImportAction(action) {
  return {
    CREATE: "新增",
    UPDATE: "更新",
    UNCHANGED: "不变",
    INVALID: "无效"
  }[action] || action;
}

async function toggleGateway(gateway) {
  if (!gatewayActionRequester.value || !gatewayActionReason.value) {
    actionMessage.value = "支付网关提交复核失败：请填写申请人声明和申请原因。";
    return;
  }
  const enabled = gateway.status !== "ENABLED";
  activeConfigCode.value = gateway.gatewayCode;
  actionMessage.value = "";
  try {
    gatewayReviews.value = await paymentConfigApi.submitGatewayStatusReview({
      gatewayCode: gateway.gatewayCode,
      enabled,
      requester: gatewayActionRequester.value,
      changeReason: gatewayActionReason.value
    });
    actionMessage.value = `支付网关 ${gateway.gatewayCode} 已提交${enabled ? "启用" : "停用"}复核，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付网关提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateGateway() {
  editingGatewayCode.value = "";
  gatewayForm.value = createGatewayForm();
  actionMessage.value = "";
}

function startEditGateway(gateway) {
  editingGatewayCode.value = gateway.gatewayCode;
  gatewayForm.value = {
    gatewayCode: gateway.gatewayCode,
    gatewayName: gateway.gatewayName,
    accessMode: gateway.accessMode,
    channelScope: gateway.channelScope,
    environmentScope: gateway.environmentScope,
    apiBaseUrl: gateway.apiBaseUrl,
    protocolType: gateway.protocolType,
    signAlgorithm: gateway.signAlgorithm,
    certificateAlias: gateway.certificateAlias,
    certificateStatus: gateway.certificateStatus,
    releaseStage: gateway.releaseStage,
    grayStrategy: gateway.grayStrategy,
    callbackWhitelist: gateway.callbackWhitelist,
    adapterRegistry: gateway.adapterRegistry,
    timeoutMs: gateway.timeoutMsValue,
    retryPolicy: gateway.retryPolicy,
    enabled: gateway.status === "ENABLED",
    priority: gateway.priority,
    changeReason: "",
    requester: ""
  };
  actionMessage.value = `正在编辑支付网关 ${gateway.gatewayCode}`;
}

async function submitGatewayForm() {
  if (!gatewayForm.value.requester || !gatewayForm.value.changeReason) {
    actionMessage.value = "支付网关提交复核失败：请填写申请人声明和申请原因。";
    return;
  }
  const gatewayCode = editingGatewayCode.value || gatewayForm.value.gatewayCode;
  activeConfigCode.value = gatewayCode;
  actionMessage.value = "";
  try {
    const candidate = {
      ...gatewayForm.value,
      timeoutMs: Number(gatewayForm.value.timeoutMs),
      priority: Number(gatewayForm.value.priority)
    };
    delete candidate.requester;
    gatewayReviews.value = await paymentConfigApi.submitGatewayReview({
      requester: gatewayForm.value.requester,
      candidate
    });
    startCreateGateway();
    actionMessage.value = `支付网关 ${gatewayCode} 已提交复核，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付网关提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadGatewayReviews() {
  isGatewayReviewLoading.value = true;
  gatewayReviewError.value = "";
  try {
    gatewayReviews.value = await paymentConfigApi.getGatewayReviews();
  } catch (error) {
    gatewayReviewError.value = error.message;
  } finally {
    isGatewayReviewLoading.value = false;
  }
}

async function handleGatewayReview(review, approved) {
  if (!gatewayReviewer.value) {
    actionMessage.value = "支付网关复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!gatewayReviewComment.value) {
    actionMessage.value = `支付网关复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
      ? `确认通过申请 ${review.requestNo} 并应用 ${review.operationType} 候选？`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `gateway-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: gatewayReviewer.value,
    reviewComment: gatewayReviewComment.value
  };
  try {
    try {
      gatewayReviews.value = approved
        ? await paymentConfigApi.approveGatewayReview(review.requestNo, payload)
        : await paymentConfigApi.rejectGatewayReview(review.requestNo, payload);
      gatewayReviewComment.value = "";
      actionMessage.value = approved
        ? `支付网关 ${review.operationType} 申请 ${review.requestNo} 已通过并应用。`
        : `支付网关申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `支付网关复核处理失败：${error.message}`;
      return;
    }
    if (!approved) {
      return;
    }
    const refreshTasks = [loadOverview()];
    if (gatewayVersionCode.value) {
      refreshTasks.push(loadGatewayVersions(gatewayVersionCode.value));
    }
    await Promise.all(refreshTasks);
  } finally {
    activeConfigCode.value = "";
  }
}

async function submitControlPolicyStatusReview(policy) {
  if (!controlPolicyActionRequester.value || !controlPolicyActionReason.value) {
    actionMessage.value = "支付控制策略启停申请失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = policy.status !== "ENABLED";
  if (!window.confirm(`确认提交支付控制策略 ${policy.sourceAppId} 的${enabled ? "启用" : "停用"}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = policy.sourceAppId;
  actionMessage.value = "";
  try {
    controlPolicyReviews.value = await paymentConfigApi.submitControlPolicyStatusReview({
      sourceAppId: policy.sourceAppId,
      enabled,
      requester: controlPolicyActionRequester.value,
      changeReason: controlPolicyActionReason.value
    });
    controlPolicyActionReason.value = "";
    actionMessage.value = `支付控制策略 ${policy.sourceAppId} 的${enabled ? "启用" : "停用"}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付控制策略启停申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateControlPolicy() {
  editingControlPolicyId.value = "";
  controlPolicyForm.value = createControlPolicyForm();
  actionMessage.value = "";
}

function startEditControlPolicy(policy) {
  editingControlPolicyId.value = policy.sourceAppId;
  controlPolicyForm.value = {
    sourceAppId: policy.sourceAppId,
    sourceAppName: policy.sourceAppName,
    allowedPaymentMethods: policy.allowedPaymentMethods,
    allowedChannelCodes: policy.allowedChannelCodes,
    allowedMerchantNos: policy.allowedMerchantNos,
    minuteSubmitLimit: policy.minuteSubmitLimit,
    interfaceMinuteSubmitLimit: policy.interfaceMinuteSubmitLimit,
    tokenAuthRequired: policy.tokenAuthRequired === "开启",
    accessTokenValue: "",
    strictMode: policy.strictMode === "开启",
    enabled: policy.status === "ENABLED",
    changeReason: "",
    requester: ""
  };
  actionMessage.value = `正在编辑支付控制策略 ${policy.sourceAppId}；访问令牌留空表示保持现值。`;
}

async function submitControlPolicyForm() {
  if (!controlPolicyForm.value.requester || !controlPolicyForm.value.changeReason) {
    actionMessage.value = "支付控制策略提交复核失败：请填写申请人声明和申请原因。";
    return;
  }
  if (!editingControlPolicyId.value
      && controlPolicyForm.value.tokenAuthRequired
      && !controlPolicyForm.value.accessTokenValue) {
    actionMessage.value = "支付控制策略提交复核失败：新增且开启令牌鉴权时必须填写访问令牌。";
    return;
  }
  const sourceAppId = editingControlPolicyId.value || controlPolicyForm.value.sourceAppId;
  activeConfigCode.value = sourceAppId;
  actionMessage.value = "";
  try {
    const candidate = {
      ...controlPolicyForm.value,
      minuteSubmitLimit: Number(controlPolicyForm.value.minuteSubmitLimit),
      interfaceMinuteSubmitLimit: Number(controlPolicyForm.value.interfaceMinuteSubmitLimit)
    };
    delete candidate.requester;
    controlPolicyReviews.value = await paymentConfigApi.submitControlPolicyReview({
      requester: controlPolicyForm.value.requester,
      candidate
    });
    startCreateControlPolicy();
    actionMessage.value = `支付控制策略 ${sourceAppId} 已提交复核，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付控制策略提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadControlPolicyReviews() {
  isControlPolicyReviewLoading.value = true;
  controlPolicyReviewError.value = "";
  try {
    controlPolicyReviews.value = await paymentConfigApi.getControlPolicyReviews();
  } catch (error) {
    controlPolicyReviewError.value = error.message;
  } finally {
    isControlPolicyReviewLoading.value = false;
  }
}

async function handleControlPolicyReview(review, approved) {
  if (!controlPolicyReviewer.value) {
    actionMessage.value = "支付控制策略复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!controlPolicyReviewComment.value) {
    actionMessage.value = `支付控制策略复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
    ? `确认通过申请 ${review.requestNo} 并应用 ${review.operationType} 候选？通过后自检状态重置为 WARN。`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `control-policy-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: controlPolicyReviewer.value,
    reviewComment: controlPolicyReviewComment.value
  };
  try {
    try {
      controlPolicyReviews.value = approved
        ? await paymentConfigApi.approveControlPolicyReview(review.requestNo, payload)
        : await paymentConfigApi.rejectControlPolicyReview(review.requestNo, payload);
      controlPolicyReviewComment.value = "";
      actionMessage.value = approved
        ? `支付控制策略 ${review.operationType} 申请 ${review.requestNo} 已通过并应用，请重新执行自检。`
        : `支付控制策略申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `支付控制策略复核处理失败：${error.message}`;
      return;
    }
    if (!approved) {
      return;
    }
    const refreshTasks = [loadOverview()];
    if (controlPolicyVersionSourceAppId.value) {
      refreshTasks.push(loadControlPolicyVersions(controlPolicyVersionSourceAppId.value));
    }
    await Promise.all(refreshTasks);
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadControlPolicyVersions(sourceAppId) {
  const requestId = ++controlPolicyVersionRequestId;
  if (controlPolicyVersionSourceAppId.value !== sourceAppId) {
    controlPolicyVersions.value = [];
  }
  controlPolicyVersionSourceAppId.value = sourceAppId;
  isControlPolicyVersionLoading.value = true;
  controlPolicyVersionError.value = "";
  try {
    const versions = await paymentConfigApi.getControlPolicyVersions(sourceAppId);
    if (requestId === controlPolicyVersionRequestId
        && controlPolicyVersionSourceAppId.value === sourceAppId) {
      controlPolicyVersions.value = versions;
    }
  } catch (error) {
    if (requestId === controlPolicyVersionRequestId
        && controlPolicyVersionSourceAppId.value === sourceAppId) {
      controlPolicyVersionError.value = error.message;
    }
  } finally {
    if (requestId === controlPolicyVersionRequestId) {
      isControlPolicyVersionLoading.value = false;
    }
  }
}

function closeControlPolicyVersions() {
  controlPolicyVersionRequestId += 1;
  controlPolicyVersionSourceAppId.value = "";
  controlPolicyVersions.value = [];
  isControlPolicyVersionLoading.value = false;
  controlPolicyVersionError.value = "";
}

async function rollbackControlPolicyVersion(version) {
  const sourceAppId = controlPolicyVersionSourceAppId.value;
  if (!controlPolicyActionRequester.value || !controlPolicyActionReason.value) {
    actionMessage.value = "提交控制策略回滚复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  if (!window.confirm(`确认提交控制策略 ${sourceAppId} 回滚到 V${version.versionNo} 的复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = `${sourceAppId}:rollback`;
  actionMessage.value = "";
  try {
    controlPolicyReviews.value = await paymentConfigApi.rollbackControlPolicy(sourceAppId, {
      versionNo: version.versionNo,
      requester: controlPolicyActionRequester.value,
      changeReason: controlPolicyActionReason.value
    });
    controlPolicyActionReason.value = "";
    actionMessage.value = `控制策略 ${sourceAppId} 回滚到 V${version.versionNo} 的申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `控制策略回滚申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function toggleAlertProvider(provider) {
  if (!alertProviderActionRequester.value || !alertProviderActionReason.value) {
    actionMessage.value = "供应商启停申请失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = provider.status !== "ENABLED";
  const actionLabel = enabled ? "启用" : "停用";
  if (!window.confirm(`确认提交供应商 ${provider.providerCode} 的${actionLabel}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = provider.providerCode;
  actionMessage.value = "";
  try {
    alertProviderReviews.value = await paymentConfigApi.toggleAlertProvider({
      providerCode: provider.providerCode,
      enabled,
      requester: alertProviderActionRequester.value,
      changeReason: alertProviderActionReason.value
    });
    alertProviderActionReason.value = "";
    actionMessage.value = `供应商 ${provider.providerCode} 的${actionLabel}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `供应商启停申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateAlertProvider() {
  editingAlertProviderCode.value = "";
  alertProviderForm.value = createAlertProviderForm();
  actionMessage.value = "";
}

function startEditAlertProvider(provider) {
  editingAlertProviderCode.value = provider.providerCode;
  alertProviderForm.value = {
    ...createAlertProviderForm(),
    providerCode: provider.providerCode,
    providerName: provider.providerName,
    channelCode: provider.channelCode,
    endpointAlias: provider.endpointAlias,
    templateCode: provider.templateCode,
    templateBody: provider.templateBody,
    routeRule: provider.routeRule,
    routePriority: provider.routePriority,
    retryPolicy: provider.retryPolicy,
    rateLimitPolicy: provider.rateLimitPolicy,
    enabled: provider.status === "ENABLED"
  };
  actionMessage.value = `正在编辑供应商 ${provider.providerCode}`;
}

async function submitAlertProviderForm() {
  if (!alertProviderForm.value.requester || !alertProviderForm.value.changeReason) {
    actionMessage.value = "供应商提交复核失败：请填写申请人声明和申请原因。";
    return;
  }
  const providerCode = editingAlertProviderCode.value || alertProviderForm.value.providerCode;
  activeConfigCode.value = providerCode;
  actionMessage.value = "";
  try {
    const candidate = {
      ...alertProviderForm.value,
      routePriority: Number(alertProviderForm.value.routePriority)
    };
    const updateOperation = Boolean(editingAlertProviderCode.value);
    alertProviderReviews.value = updateOperation
      ? await paymentConfigApi.updateAlertProvider(editingAlertProviderCode.value, candidate)
      : await paymentConfigApi.createAlertProvider(candidate);
    startCreateAlertProvider();
    actionMessage.value = updateOperation
      ? `供应商 ${providerCode} 的修改候选已提交，运行态尚未改变。`
      : `供应商 ${providerCode} 的新增候选已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `供应商提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadAlertProviderReviews() {
  isAlertProviderReviewLoading.value = true;
  alertProviderReviewError.value = "";
  try {
    alertProviderReviews.value = await paymentConfigApi.getAlertProviderReviews();
  } catch (error) {
    alertProviderReviewError.value = error.message;
  } finally {
    isAlertProviderReviewLoading.value = false;
  }
}

async function handleAlertProviderReview(review, approved) {
  if (!alertProviderReviewer.value) {
    actionMessage.value = "供应商复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!alertProviderReviewComment.value) {
    actionMessage.value = `供应商复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
    ? `确认通过申请 ${review.requestNo} 并将候选供应商配置写入运行态？`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `alert-provider-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: alertProviderReviewer.value,
    reviewComment: alertProviderReviewComment.value
  };
  try {
    try {
      alertProviderReviews.value = approved
        ? await paymentConfigApi.approveAlertProviderReview(review.requestNo, payload)
        : await paymentConfigApi.rejectAlertProviderReview(review.requestNo, payload);
      alertProviderReviewComment.value = "";
      actionMessage.value = approved
        ? `供应商申请 ${review.requestNo} 已通过，候选配置已写入运行态。`
        : `供应商申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `供应商复核处理失败：${error.message}`;
      return;
    }
    if (approved) {
      await loadOverview();
    }
  } finally {
    activeConfigCode.value = "";
  }
}

async function runControlPolicySelfCheck(policy) {
  activeConfigCode.value = policy.sourceAppId;
  actionMessage.value = "";
  try {
    applyOverview(await paymentConfigApi.runControlPolicySelfCheck(policy.sourceAppId));
    actionMessage.value = `支付控制策略 ${policy.sourceAppId} 自检已完成。`;
  } catch (error) {
    actionMessage.value = `支付控制策略 ${policy.sourceAppId} 自检失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateProtocol() {
  editingProtocolCode.value = "";
  protocolForm.value = createProtocolForm();
  actionMessage.value = "";
}

function startEditProtocol(protocol) {
  editingProtocolCode.value = protocol.protocolCode;
  protocolForm.value = {
    protocolCode: protocol.protocolCode,
    protocolName: protocol.protocolName,
    protocolType: protocol.protocolType,
    protocolTypeName: protocol.protocolTypeName,
    templateCode: protocol.templateCode,
    templateName: protocol.templateName,
    templateVersion: protocol.templateVersion,
    signMode: protocol.signMode,
    signElementSpec: protocol.signElementSpec,
    eSignatureProvider: protocol.eSignatureProvider,
    sceneScope: protocol.sceneScope,
    channelScope: protocol.channelScope,
    merchantAckRequired: protocol.merchantAckRequired,
    riskControlTag: protocol.riskControlTag,
    protocolBody: protocol.protocolBody,
    priority: protocol.priority,
    enabled: protocol.status === "ENABLED",
    changeReason: "",
    requester: ""
  };
  actionMessage.value = `正在编辑协议 ${protocol.protocolCode}`;
}

async function submitProtocolForm() {
  if (!protocolForm.value.requester) {
    actionMessage.value = "支付协议提交复核失败：请填写申请人声明。";
    return;
  }
  if (!protocolForm.value.changeReason) {
    actionMessage.value = "支付协议提交复核失败：请填写申请原因。";
    return;
  }
  activeConfigCode.value = editingProtocolCode.value || protocolForm.value.protocolCode;
  actionMessage.value = "";
  try {
    const candidate = {
      ...protocolForm.value,
      priority: Number(protocolForm.value.priority || 99)
    };
    delete candidate.requester;
    protocolReviews.value = await paymentConfigApi.submitProtocolReview({
      requester: protocolForm.value.requester,
      candidate
    });
    const submittedProtocolCode = editingProtocolCode.value || protocolForm.value.protocolCode;
    startCreateProtocol();
    actionMessage.value = `支付协议 ${submittedProtocolCode} 已提交复核，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付协议提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadProtocolVersions(protocolCode) {
  const requestId = ++protocolVersionRequestId;
  if (protocolVersionCode.value !== protocolCode) {
    protocolVersions.value = [];
  }
  protocolVersionCode.value = protocolCode;
  isProtocolVersionLoading.value = true;
  protocolVersionError.value = "";
  try {
    const versions = await paymentConfigApi.getProtocolVersions(protocolCode);
    if (requestId === protocolVersionRequestId && protocolVersionCode.value === protocolCode) {
      protocolVersions.value = versions;
    }
  } catch (error) {
    if (requestId === protocolVersionRequestId && protocolVersionCode.value === protocolCode) {
      protocolVersionError.value = error.message;
    }
  } finally {
    if (requestId === protocolVersionRequestId) {
      isProtocolVersionLoading.value = false;
    }
  }
}

function closeProtocolVersions() {
  protocolVersionRequestId += 1;
  protocolVersionCode.value = "";
  protocolVersions.value = [];
  isProtocolVersionLoading.value = false;
  protocolVersionError.value = "";
}

async function rollbackProtocolVersion(version) {
  const protocolCode = protocolVersionCode.value;
  if (!protocolActionRequester.value || !protocolActionReason.value) {
    actionMessage.value = "提交协议回滚复核失败：请先填写申请人声明和申请原因。";
    return;
  }
  if (!window.confirm(`确认提交支付协议 ${protocolCode} 回滚到 V${version.versionNo} 的复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = `${protocolCode}:rollback`;
  actionMessage.value = "";
  try {
    protocolReviews.value = await paymentConfigApi.rollbackProtocol(protocolCode, {
      versionNo: version.versionNo,
      requester: protocolActionRequester.value,
      changeReason: protocolActionReason.value
    });
    protocolActionReason.value = "";
    actionMessage.value = `支付协议 ${protocolCode} 回滚到 V${version.versionNo} 的申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `支付协议回滚申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadProtocolReviews() {
  isProtocolReviewLoading.value = true;
  protocolReviewErrorMessage.value = "";
  try {
    protocolReviews.value = await paymentConfigApi.getProtocolReviews();
  } catch (error) {
    protocolReviewErrorMessage.value = error.message;
  } finally {
    isProtocolReviewLoading.value = false;
  }
}

function formatProtocolOperation(operationType) {
  const labels = {
    CREATE: "新增",
    UPDATE: "修改",
    STATUS_CHANGE: "启停",
    ROLLBACK: "回滚"
  };
  return labels[operationType] || operationType;
}

async function handleProtocolReview(review, approved) {
  if (!protocolReviewer.value) {
    actionMessage.value = "协议复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!protocolReviewComment.value) {
    actionMessage.value = `协议复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(
    approved
      ? `确认通过申请 ${review.requestNo} 并将候选协议写入运行态？`
      : `确认驳回申请 ${review.requestNo}？运行态不会改变。`
  );
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `protocol-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: protocolReviewer.value,
    reviewComment: protocolReviewComment.value
  };
  try {
    try {
      protocolReviews.value = approved
        ? await paymentConfigApi.approveProtocolReview(review.requestNo, payload)
        : await paymentConfigApi.rejectProtocolReview(review.requestNo, payload);
      protocolReviewComment.value = "";
      actionMessage.value = approved
        ? `协议复核申请 ${review.requestNo} 已通过，候选协议已写入运行态。`
        : `协议复核申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `协议复核处理失败：${error.message}`;
      return;
    }
    if (!approved) {
      return;
    }
    const refreshTasks = [loadOverview()];
    if (protocolVersionCode.value) {
      refreshTasks.push(loadProtocolVersions(protocolVersionCode.value));
    }
    await Promise.all(refreshTasks);
  } finally {
    activeConfigCode.value = "";
  }
}

async function toggleIssueDutyRoster(roster) {
  if (!issueDutyRosterActionRequester.value || !issueDutyRosterActionReason.value) {
    actionMessage.value = "值班路由启停申请失败：请先填写申请人声明和申请原因。";
    return;
  }
  const enabled = roster.status !== "ENABLED";
  const actionLabel = enabled ? "启用" : "停用";
  if (!window.confirm(`确认提交值班路由 ${roster.rosterCode} 的${actionLabel}复核申请？运行态暂不改变。`)) {
    return;
  }
  activeConfigCode.value = roster.rosterCode;
  actionMessage.value = "";
  try {
    issueDutyRosterReviews.value = await paymentConfigApi.toggleIssueDutyRoster({
      rosterCode: roster.rosterCode,
      enabled,
      requester: issueDutyRosterActionRequester.value,
      changeReason: issueDutyRosterActionReason.value
    });
    issueDutyRosterActionReason.value = "";
    actionMessage.value = `值班路由 ${roster.rosterCode} 的${actionLabel}申请已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `值班路由启停申请失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

function startCreateRoster() {
  editingRosterCode.value = "";
  rosterForm.value = createRosterForm();
  actionMessage.value = "";
}

function startEditRoster(roster) {
  editingRosterCode.value = roster.rosterCode;
  rosterForm.value = {
    ...createRosterForm(),
    rosterCode: roster.rosterCode,
    issueType: roster.issueType,
    severity: roster.severity,
    responsibilityGroup: roster.responsibilityGroup,
    receiver: roster.receiver,
    notifyChannels: roster.notifyChannels,
    escalationLevel: roster.escalationLevel,
    escalationReceiver: roster.escalationReceiver,
    escalationPolicy: roster.escalationPolicy,
    escalationTimeoutMinutes: roster.escalationTimeoutMinutes,
    scheduleTag: roster.scheduleTag,
    effectiveStartHour: roster.effectiveStartHour,
    effectiveEndHour: roster.effectiveEndHour,
    weekdayScope: roster.weekdayScope || "1,2,3,4,5,6,7",
    holidayStrategy: roster.holidayStrategy || "ALL_DAYS",
    enabled: roster.status === "ENABLED",
    requester: "",
    changeReason: ""
  };
  actionMessage.value = `正在编辑值班路由 ${roster.rosterCode}`;
}

async function submitRosterForm() {
  if (!rosterForm.value.requester || !rosterForm.value.changeReason) {
    actionMessage.value = "值班路由提交复核失败：请填写申请人声明和申请原因。";
    return;
  }
  activeConfigCode.value = editingRosterCode.value || rosterForm.value.rosterCode;
  actionMessage.value = "";
  try {
    const submittedRosterCode = editingRosterCode.value || rosterForm.value.rosterCode;
    const updateOperation = Boolean(editingRosterCode.value);
    issueDutyRosterReviews.value = editingRosterCode.value
      ? await paymentConfigApi.updateIssueDutyRoster(editingRosterCode.value, rosterForm.value)
      : await paymentConfigApi.createIssueDutyRoster(rosterForm.value);
    startCreateRoster();
    actionMessage.value = updateOperation
      ? `值班路由 ${submittedRosterCode} 的修改候选已提交，运行态尚未改变。`
      : `值班路由 ${submittedRosterCode} 的新增候选已提交，运行态尚未改变。`;
  } catch (error) {
    actionMessage.value = `值班路由提交复核失败：${error.message}`;
  } finally {
    activeConfigCode.value = "";
  }
}

async function loadIssueDutyRosterReviews() {
  isIssueDutyRosterReviewLoading.value = true;
  issueDutyRosterReviewError.value = "";
  try {
    issueDutyRosterReviews.value = await paymentConfigApi.getIssueDutyRosterReviews();
  } catch (error) {
    issueDutyRosterReviewError.value = error.message;
  } finally {
    isIssueDutyRosterReviewLoading.value = false;
  }
}

async function handleIssueDutyRosterReview(review, approved) {
  if (!issueDutyRosterReviewer.value) {
    actionMessage.value = "值班路由复核处理失败：复核人声明不能为空。";
    return;
  }
  if (!issueDutyRosterReviewComment.value) {
    actionMessage.value = `值班路由复核处理失败：${approved ? "复核意见" : "驳回原因"}不能为空。`;
    return;
  }
  const confirmed = window.confirm(approved
    ? `确认通过申请 ${review.requestNo} 并将候选值班路由写入运行态？`
    : `确认驳回申请 ${review.requestNo}？运行态不会改变。`);
  if (!confirmed) {
    return;
  }
  activeConfigCode.value = `issue-duty-roster-review:${review.requestNo}`;
  actionMessage.value = "";
  const payload = {
    reviewer: issueDutyRosterReviewer.value,
    reviewComment: issueDutyRosterReviewComment.value
  };
  try {
    try {
      issueDutyRosterReviews.value = approved
        ? await paymentConfigApi.approveIssueDutyRosterReview(review.requestNo, payload)
        : await paymentConfigApi.rejectIssueDutyRosterReview(review.requestNo, payload);
      issueDutyRosterReviewComment.value = "";
      actionMessage.value = approved
        ? `值班路由申请 ${review.requestNo} 已通过，候选配置已写入运行态。`
        : `值班路由申请 ${review.requestNo} 已驳回，运行态未改变。`;
    } catch (error) {
      actionMessage.value = `值班路由复核处理失败：${error.message}`;
      return;
    }
    if (approved) {
      await loadOverview();
    }
  } finally {
    activeConfigCode.value = "";
  }
}

onMounted(() => {
  loadOverview();
  loadChannelReviews();
  loadRouteRuleReviews();
  loadProtocolReviews();
  loadReturnCodeReviews();
  loadReturnCodeImportReviews();
  loadGatewayReviews();
  loadControlPolicyReviews();
  loadAlertProviderReviews();
  loadIssueDutyRosterReviews();
});

function exportGovernanceSnapshot(section = "ALL") {
  const exportUrl = paymentConfigApi.buildExportUrl(section);
  window.open(exportUrl, "_blank", "noopener");
}
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付配置中心</h2>
        <p>统一管理支付渠道、路由规则、支付协议、返回码映射和支付网关接入配置</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" :disabled="isLoading" @click="loadOverview">刷新配置</button>
        <button class="button secondary" @click="exportGovernanceSnapshot()">导出治理快照</button>
        <span class="badge info">配置中心 V1.23</span>
      </div>
    </div>

    <section class="panel">
      <div v-if="overviewErrorMessage" class="error-banner">
        {{ hasLoadedOverview ? "支付配置刷新失败，当前展示上次成功快照：" : "支付配置加载失败：" }}{{ overviewErrorMessage }}
      </div>
      <div
        v-if="actionMessage"
        :class="actionMessagePresentation.className"
        :role="actionMessagePresentation.role"
      >
        {{ actionMessage }}
      </div>

      <div v-if="isLoading && !hasLoadedOverview" class="state-box">支付配置加载中...</div>
      <div v-else-if="!hasLoadedOverview" class="state-box">当前没有可展示的支付配置快照</div>

      <fieldset
        v-if="hasLoadedOverview"
        class="config-content"
        :disabled="Boolean(activeConfigCode) || isLoading || Boolean(overviewErrorMessage)"
      >
        <section class="card-grid" style="margin-bottom: 16px;">
          <article class="card">
            <p class="card-title">启用渠道数</p>
            <p class="card-value">{{ governanceMetrics.enabledChannelCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">启用路由规则</p>
            <p class="card-value">{{ governanceMetrics.enabledRouteRuleCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">启用支付协议</p>
            <p class="card-value">{{ governanceMetrics.enabledProtocolCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">启用网关配置</p>
            <p class="card-value">{{ governanceMetrics.enabledGatewayCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">启用控制策略</p>
            <p class="card-value">{{ governanceMetrics.enabledControlPolicyCount }}</p>
          </article>
          <article class="card">
            <p class="card-title">值班路由数</p>
            <p class="card-value">{{ governanceMetrics.enabledIssueDutyRosterCount }}</p>
          </article>
        </section>

        <section class="panel overview-panel">
          <div class="section-title">
            <div>
              <h3>治理风险总览</h3>
              <p class="meta">按渠道准入、控制策略自检、通知供应商和值班路由四类治理要素统一观察配置健康度</p>
            </div>
          </div>
          <div class="overview-grid">
            <article class="overview-card danger">
              <p class="overview-title">控制策略失败</p>
              <strong>{{ governanceMetrics.failedControlPolicyCount }}</strong>
              <span>存在 FAIL 时应优先修复来源应用准入、商户号授权或渠道权限配置，避免主链路带病发布。</span>
            </article>
            <article class="overview-card warn">
              <p class="overview-title">控制策略告警</p>
              <strong>{{ governanceMetrics.warningControlPolicyCount }}</strong>
              <span>表示自检存在 WARN，建议联查网关接入、渠道启停和通知值班路由是否处于一致状态。</span>
            </article>
            <article class="overview-card info">
              <p class="overview-title">归档返回码 / 通知治理</p>
              <strong>{{ governanceMetrics.archivedReturnCodeCount }} / {{ governanceMetrics.enabledAlertProviderCount }}</strong>
              <span>用于判断返回码版本治理是否持续沉淀，以及异常告警外发能力是否具备最小可用覆盖面。</span>
            </article>
          </div>
        </section>

        <div class="section-title">
          <div>
            <h3>支付渠道配置</h3>
            <p class="meta">控制渠道商户号、适用场景、默认路由权重、单日限额和启停状态</p>
          </div>
          <div class="toolbar-actions">
            <button class="button secondary" @click="exportGovernanceSnapshot('CHANNELS')">导出渠道</button>
            <button class="button secondary" @click="startCreateChannel">新增渠道</button>
            <button class="button secondary" @click="loadOverview">刷新</button>
          </div>
        </div>

        <div class="panel" style="margin-bottom: 16px;">
          <div class="section-title">
            <div>
              <h3>{{ editingChannelCode ? "编辑支付渠道" : "新增支付渠道" }}</h3>
              <p class="meta">完整候选经异人声明复核通过后才写入运行态；密钥不会出现在复核列表</p>
            </div>
          </div>
          <div class="filter-grid">
            <label>
              渠道编码
              <input v-model.trim="channelForm.channelCode" :disabled="Boolean(editingChannelCode)" maxlength="64" />
            </label>
            <label>
              渠道名称
              <input v-model.trim="channelForm.channelName" maxlength="128" />
            </label>
            <label>
              支付方式
              <input v-model.trim="channelForm.paymentMethod" maxlength="64" />
            </label>
            <label>
              商户号
              <input v-model.trim="channelForm.merchantNo" maxlength="128" />
            </label>
            <label>
              应用标识
              <input v-model.trim="channelForm.merchantAppId" maxlength="128" />
            </label>
            <label>
              签名算法
              <select v-model="channelForm.callbackSignAlgorithm">
                <option value="HMAC-SHA256">HMAC-SHA256</option>
                <option value="RSA2">RSA2</option>
              </select>
            </label>
            <label>
              回调验签密钥
              <input v-model.trim="channelForm.callbackSecret" type="password" maxlength="256" :placeholder="editingChannelCode ? '留空保持现值' : '新增渠道必填'" />
            </label>
            <label class="wide-field">
              RSA2 验签公钥
              <textarea v-model.trim="channelForm.callbackPublicKey" rows="3" :placeholder="editingChannelCode ? '留空保持现值' : 'RSA2 算法必填'" />
            </label>
            <label class="wide-field">
              回调通知地址
              <input v-model.trim="channelForm.callbackNotifyUrl" maxlength="256" placeholder="https://..." />
            </label>
            <label>
              证书档案
              <input v-model.trim="channelForm.certificateProfile" maxlength="128" />
            </label>
            <label>
              验签时间窗（秒）
              <input v-model.number="channelForm.notifySignWindowSec" type="number" min="1" />
            </label>
            <label>
              原路退款时效（天）
              <input v-model.number="channelForm.refundWindowDays" type="number" min="1" />
            </label>
            <label>
              风控标签
              <input v-model.trim="channelForm.riskControlTag" maxlength="128" />
            </label>
            <label>
              适用场景
              <input v-model.trim="channelForm.sceneScope" maxlength="128" />
            </label>
            <label>
              单日限额
              <input v-model="channelForm.dailyLimit" type="number" min="0" step="0.01" />
            </label>
            <label>
              优先级
              <input v-model.number="channelForm.priority" type="number" min="0" />
            </label>
            <label>
              路由权重
              <input v-model.number="channelForm.routeWeight" type="number" min="0" max="100" />
            </label>
            <label>
              启用状态
              <select v-model="channelForm.enabled">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
            <label>
              申请人声明
              <input v-model.trim="channelForm.requester" maxlength="64" />
            </label>
            <label class="wide-field">
              申请原因
              <input v-model.trim="channelForm.changeReason" maxlength="255" />
            </label>
          </div>
          <div style="display: flex; gap: 12px; margin-top: 16px;">
            <button class="button primary" :disabled="activeConfigCode === (editingChannelCode || channelForm.channelCode)" @click="submitChannelForm">
              {{ editingChannelCode ? "提交修改复核" : "提交新增复核" }}
            </button>
            <button class="button secondary" @click="startCreateChannel">重置表单</button>
          </div>
        </div>

        <div class="panel" style="margin-bottom: 16px;">
          <div class="section-title">
            <div>
              <h3>支付渠道配置变更复核</h3>
              <p class="meta">新增、编辑、启停和历史回滚共享唯一待办；批准后追加不可变版本</p>
            </div>
            <button class="button secondary" :disabled="isChannelReviewLoading" @click="loadChannelReviews">
              {{ isChannelReviewLoading ? "加载中..." : "刷新申请" }}
            </button>
          </div>
          <div class="filter-grid">
            <label>
              申请人声明
              <input v-model.trim="channelActionRequester" maxlength="64" placeholder="提交启停或回滚时必填" />
            </label>
            <label>
              申请原因
              <input v-model.trim="channelActionReason" maxlength="255" placeholder="提交启停或回滚时必填" />
            </label>
            <label>
              复核人声明
              <input v-model.trim="channelReviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
            </label>
            <label>
              复核意见 / 驳回原因
              <input v-model.trim="channelReviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
            </label>
          </div>
          <div v-if="channelReviewError" class="error-banner">
            {{ channelReviews.length ? "支付渠道复核申请刷新失败，当前展示上次成功快照：" : "支付渠道复核申请加载失败：" }}{{ channelReviewError }}
          </div>
          <div v-if="isChannelReviewLoading" class="state-box">支付渠道复核申请加载中...</div>
          <div v-else-if="!channelReviews.length && !channelReviewError" class="state-box">当前暂无支付渠道配置复核申请</div>
          <div v-else-if="channelReviews.length" class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>申请号</th>
                  <th>操作</th>
                  <th>渠道</th>
                  <th>候选状态</th>
                  <th>申请声明</th>
                  <th>复核状态</th>
                  <th>复核记录</th>
                  <th>时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="review in channelReviews" :key="review.requestNo">
                  <td>{{ review.requestNo }}</td>
                  <td>{{ review.operationType }}</td>
                  <td>
                    {{ review.channelCode }} / {{ review.channelName }}
                    <div class="muted-text">{{ review.paymentMethod }} / {{ review.merchantNo }}</div>
                    <div class="muted-text">密钥 {{ review.callbackSecretMasked }} / 公钥 {{ review.callbackPublicKeyConfigured ? "已配置" : "未配置" }}</div>
                    <div class="muted-text">基线 {{ review.baseVersionNo ? `V${review.baseVersionNo}` : "新增" }} / {{ review.baseFingerprint?.slice(0, 12) || "-" }}</div>
                    <div v-if="review.sourceVersionNo" class="muted-text">回滚来源 V{{ review.sourceVersionNo }}</div>
                  </td>
                  <td><span :class="['badge', review.candidateStatusType]">{{ review.candidateStatus }}</span></td>
                  <td>
                    {{ review.requester }}
                    <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                  </td>
                  <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                  <td>
                    {{ review.reviewer || "-" }}
                    <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                  </td>
                  <td>
                    {{ review.createdAt }}
                    <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                  </td>
                  <td>
                    <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                      <button
                        class="link-button"
                        :disabled="Boolean(activeConfigCode)"
                        @click="handleChannelReview(review, true)"
                      >
                        通过并应用
                      </button>
                      <button
                        class="link-button"
                        :disabled="Boolean(activeConfigCode)"
                        @click="handleChannelReview(review, false)"
                      >
                        驳回
                      </button>
                    </template>
                    <span v-else class="muted-text">已处理</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
                <tr>
                  <th>渠道编码</th>
                  <th>渠道名称</th>
                  <th>支付方式</th>
                  <th>商户号</th>
                  <th>应用标识</th>
                  <th>回调地址</th>
                  <th>签名算法</th>
                  <th>验签密钥</th>
                  <th>证书档案</th>
                <th>验签时间窗</th>
                <th>退款时效</th>
                <th>风控标签</th>
                <th>适用场景</th>
                <th>单日限额</th>
                <th>优先级</th>
                <th>路由权重</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
                <tr v-for="channel in channels" :key="channel.channelCode">
                  <td>{{ channel.channelCode }}</td>
                  <td>{{ channel.channelName }}</td>
                  <td>{{ channel.paymentMethod }}</td>
                  <td>{{ channel.merchantNo }}</td>
                  <td>{{ channel.merchantAppId }}</td>
                  <td class="flow-summary-cell">{{ channel.callbackNotifyUrl }}</td>
                  <td>{{ channel.callbackSignAlgorithm }}</td>
                  <td>{{ channel.callbackSecretMasked }}</td>
                <td>{{ channel.certificateProfile }}</td>
                <td>{{ channel.notifySignWindow }}</td>
                <td>{{ channel.refundWindow }}</td>
                <td class="flow-summary-cell">{{ channel.riskControlTag }}</td>
                <td>{{ channel.sceneScope }}</td>
                <td>{{ channel.dailyLimit }}</td>
                <td>{{ channel.priority }}</td>
                <td>{{ channel.routeWeight }}</td>
                <td><span :class="['badge', channel.statusType]">{{ channel.status }}</span></td>
                <td>{{ channel.updatedAt }}</td>
                <td>
                  <button
                    class="link-button"
                    :disabled="activeConfigCode === channel.channelCode"
                    @click="startEditChannel(channel)"
                  >
                    编辑
                  </button>
                  <button
                    class="link-button"
                    :disabled="activeConfigCode === channel.channelCode"
                    @click="submitChannelStatusReview(channel)"
                  >
                    {{ channel.status === "ENABLED" ? "申请停用" : "申请启用" }}
                  </button>
                  <button
                    class="link-button"
                    :disabled="Boolean(activeConfigCode)"
                    @click="loadChannelVersions(channel.channelCode)"
                  >
                    版本历史
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="channelVersionCode" class="panel" style="margin-top: 16px;">
          <div class="section-title">
            <div>
              <h3>{{ channelVersionCode }} 版本历史</h3>
              <p class="meta">历史快照不可修改且不展示回调密钥、公钥原文；回滚复核通过后恢复完整渠道配置并追加新版本</p>
            </div>
            <button class="button secondary" @click="closeChannelVersions">关闭</button>
          </div>
          <div v-if="channelVersionError" class="error-banner">
            {{ channelVersions.length ? "支付渠道版本刷新失败，当前展示上次成功快照：" : "支付渠道版本加载失败：" }}{{ channelVersionError }}
          </div>
          <div v-if="isChannelVersionLoading" class="state-box">支付渠道版本加载中...</div>
          <div v-else-if="!channelVersions.length && !channelVersionError" class="state-box">当前暂无支付渠道版本记录</div>
          <div v-else-if="channelVersions.length" class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>版本</th>
                  <th>变更</th>
                  <th>渠道参数</th>
                  <th>回调安全</th>
                  <th>限制</th>
                  <th>状态</th>
                  <th>原因 / 时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="version in channelVersions" :key="version.versionNo">
                  <td>V{{ version.versionNo }}</td>
                  <td>
                    {{ version.changeType }}
                    <div class="muted-text">来源 {{ version.sourceVersionNo ? `V${version.sourceVersionNo}` : "-" }}</div>
                  </td>
                  <td class="flow-summary-cell">
                    {{ version.channelName }} / {{ version.paymentMethod }}<br />
                    {{ version.merchantNo }} / {{ version.merchantAppId }}<br />
                    {{ version.sceneScope }}
                  </td>
                  <td class="flow-summary-cell">
                    {{ version.callbackSignAlgorithm }} / 密钥 {{ version.callbackSecretConfigured ? "已配置" : "未配置" }}<br />
                    公钥 {{ version.callbackPublicKeyConfigured ? "已配置" : "未配置" }} / {{ version.certificateProfile }}<br />
                    {{ version.callbackNotifyUrl }}
                  </td>
                  <td>
                    日限额 {{ version.dailyLimit }}<br />
                    验签 {{ version.notifySignWindowSec }} 秒 / 退款 {{ version.refundWindowDays }} 天<br />
                    优先级 {{ version.priority }} / 权重 {{ version.routeWeight }}
                  </td>
                  <td><span :class="['badge', version.statusType]">{{ version.status }}</span></td>
                  <td class="flow-summary-cell">{{ version.changeReason }}<br />{{ version.createdAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="Boolean(activeConfigCode) || version.versionNo === channelVersions[0]?.versionNo"
                      @click="rollbackChannelVersion(version)"
                    >
                      回滚到此版本
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>支付路由规则</h3>
              <p class="meta">支持括号、AND / OR / NOT、IN、比较运算；非法表达式不命中并继续后续规则</p>
            </div>
            <div class="toolbar-actions">
              <button class="button secondary" @click="exportGovernanceSnapshot('ROUTE_RULES')">导出路由</button>
              <button class="button secondary" :disabled="isConflictLoading" @click="detectRouteRuleConflicts">
                {{ isConflictLoading ? "检测中..." : "检测冲突" }}
              </button>
              <button class="button secondary" @click="startCreateRouteRule">新建规则</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ editingRouteRuleCode ? "编辑路由规则" : "新增路由规则" }}</h3>
                <p class="meta">提交后仅生成候选申请，异人复核通过后才修改运行态</p>
              </div>
            </div>
            <div class="filter-grid">
              <label>
                规则编码
                <input v-model.trim="routeRuleForm.ruleCode" :disabled="Boolean(editingRouteRuleCode)" placeholder="RULE_HOME_APP" />
              </label>
              <label>
                规则名称
                <input v-model.trim="routeRuleForm.ruleName" placeholder="家政 App 小额路由" />
              </label>
              <label>
                匹配场景
                <input v-model.trim="routeRuleForm.matchScene" placeholder="HOME_CLEAN/APP" />
              </label>
              <label>
                目标渠道
                <select v-model="routeRuleForm.targetChannelCode">
                  <option value="">请选择目标渠道</option>
                  <option v-for="channel in channels" :key="channel.channelCode" :value="channel.channelCode">
                    {{ channel.channelCode }} / {{ channel.channelName }}
                  </option>
                </select>
              </label>
              <label>
                兜底渠道
                <select v-model="routeRuleForm.fallbackChannelCode">
                  <option value="">无兜底渠道</option>
                  <option v-for="channel in channels" :key="channel.channelCode" :value="channel.channelCode">
                    {{ channel.channelCode }} / {{ channel.channelName }}
                  </option>
                </select>
              </label>
              <label>
                灰度比例（%）
                <input v-model.number="routeRuleForm.rolloutPercentage" type="number" min="0" max="100" step="1" />
              </label>
              <label>
                稳定分桶盐值
                <input v-model.trim="routeRuleForm.rolloutSalt" maxlength="64" placeholder="stable-v1" />
              </label>
              <label>
                优先级
                <input v-model.number="routeRuleForm.priority" type="number" min="0" />
              </label>
              <label>
                启用状态
                <select v-model="routeRuleForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
              <label class="wide-field">
                申请原因
                <input v-model.trim="routeRuleForm.changeReason" maxlength="255" placeholder="必填：说明新增或调整原因" />
              </label>
              <label>
                申请人声明
                <input v-model.trim="routeRuleForm.requester" maxlength="64" placeholder="必填：maker-a" />
              </label>
              <label class="wide-field">
                匹配表达式
                <textarea
                  v-model.trim="routeRuleForm.matchExpression"
                  rows="3"
                  placeholder="terminal IN (H5, APP) AND amount<=500"
                />
              </label>
              <label>
                样例支付方式
                <input v-model.trim="routeRuleForm.samplePaymentMethod" />
              </label>
              <label>
                样例请求渠道
                <input v-model.trim="routeRuleForm.sampleRequestedChannelCode" placeholder="可留空" />
              </label>
              <label>
                样例支付场景
                <input v-model.trim="routeRuleForm.samplePayScene" />
              </label>
              <label>
                样例终端
                <input v-model.trim="routeRuleForm.sampleTerminal" />
              </label>
              <label>
                样例金额
                <input v-model.number="routeRuleForm.sampleAmount" type="number" min="0" step="0.01" />
              </label>
              <label>
                样例客户名称
                <input v-model.trim="routeRuleForm.sampleCustomerName" />
              </label>
            </div>
            <div class="notice-banner" style="margin-top: 16px;">
              命中场景与表达式后，支付单号按规则编码、盐值稳定分桶；桶外继续后续规则，不使用当前规则兜底渠道。盐值变更会重排桶位，0% 摘流，100% 全量。
            </div>
            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button class="button secondary" :disabled="Boolean(activeConfigCode)" @click="validateRouteRuleForm">
                校验并模拟
              </button>
              <button class="button primary" :disabled="Boolean(activeConfigCode)" @click="submitRouteRuleForm">
                提交复核
              </button>
              <button class="button secondary" @click="startCreateRouteRule">重置表单</button>
            </div>
          </div>

          <div class="return-code-import-panel">
            <div class="section-title">
              <div>
                <h3>返回码状态变更复核</h3>
                <p class="meta">启停先提交候选；批准前复验完整映射基线，驳回不改变运行态</p>
              </div>
              <button
                class="button secondary"
                :disabled="isReturnCodeReviewLoading"
                @click="loadReturnCodeReviews"
              >
                {{ isReturnCodeReviewLoading ? "刷新中..." : "刷新申请" }}
              </button>
            </div>
            <div class="notice-banner">
              申请人与复核人均为自声明身份，必须不同；该约束不代表可信身份或正式审批。
            </div>
            <div class="filter-grid" style="margin-bottom: 16px;">
              <label>
                申请人声明
                <input v-model.trim="returnCodeActionRequester" maxlength="64" placeholder="提交启停申请时必填" />
              </label>
              <label>
                申请原因
                <input v-model.trim="returnCodeActionReason" maxlength="255" placeholder="提交启停申请时必填" />
              </label>
              <label>
                复核人声明
                <input v-model.trim="returnCodeReviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
              </label>
              <label>
                复核意见 / 驳回原因
                <input v-model.trim="returnCodeReviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
              </label>
            </div>
            <div v-if="returnCodeReviewError" class="error-banner">
              {{ returnCodeReviews.length ? "返回码状态复核申请刷新失败，当前展示上次成功快照：" : "返回码状态复核申请加载失败：" }}{{ returnCodeReviewError }}
            </div>
            <div v-if="isReturnCodeReviewLoading" class="state-box">返回码状态复核申请加载中...</div>
            <div v-else-if="!returnCodeReviews.length && !returnCodeReviewError" class="state-box">当前暂无返回码状态复核申请</div>
            <div v-else-if="returnCodeReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>映射</th>
                    <th>候选状态</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in returnCodeReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>
                      {{ review.channelCode }} / {{ review.channelReturnCode }}
                      <div class="muted-text">{{ review.standardizedCode }} / {{ review.mappingVersion }}</div>
                    </td>
                    <td><span :class="['badge', review.candidateStatusType]">{{ review.candidateStatus }}</span></td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleReturnCodeReview(review, true)"
                        >
                          通过并应用
                        </button>
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleReturnCodeReview(review, false)"
                        >
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>规则编码</th>
                  <th>规则名称</th>
                  <th>匹配场景</th>
                  <th>匹配表达式</th>
                  <th>目标渠道</th>
                  <th>兜底渠道</th>
                  <th>灰度桶</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="rule in routeRules" :key="rule.ruleCode">
                  <td>{{ rule.ruleCode }}</td>
                  <td>{{ rule.ruleName }}</td>
                  <td>{{ rule.matchScene }}</td>
                  <td class="flow-summary-cell">{{ rule.matchExpression }}</td>
                  <td>{{ rule.targetChannelCode }}</td>
                  <td>{{ rule.fallbackChannelCode }}</td>
                  <td>{{ rule.rolloutPercentage ?? 100 }}% / {{ rule.rolloutSalt }}</td>
                  <td>{{ rule.priority }}</td>
                  <td><span :class="['badge', rule.statusType]">{{ rule.status }}</span></td>
                  <td>{{ rule.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === rule.ruleCode"
                      @click="startEditRouteRule(rule)"
                    >
                      编辑
                    </button>
                    <button
                      class="link-button"
                      :disabled="Boolean(activeConfigCode)"
                      @click="loadRouteRuleVersions(rule.ruleCode)"
                    >
                      版本
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === rule.ruleCode"
                      @click="toggleRouteRule(rule)"
                    >
                      {{ rule.status === "ENABLED" ? "申请停用" : "申请启用" }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="isConflictLoading || hasCheckedRouteRuleConflicts" style="margin-top: 20px;">
            <div class="section-title">
              <div>
                <h3>规则冲突检测</h3>
                <p class="meta">确定冲突与遮蔽可直接定位；同优先级潜在冲突仍需人工确认表达式是否互斥</p>
              </div>
              <span v-if="hasCheckedRouteRuleConflicts" :class="['badge', routeRuleConflicts.length ? 'warn' : 'success']">
                {{ routeRuleConflicts.length ? `${routeRuleConflicts.length} 项待处理` : "未发现冲突" }}
              </span>
            </div>
            <div v-if="isConflictLoading" class="state-box">正在检测启用规则...</div>
            <div v-else-if="!routeRuleConflicts.length" class="state-box">
              当前启用规则未发现无效表达式、确定冲突、重复、遮蔽或同优先级潜在冲突
            </div>
            <div v-else class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>级别</th>
                    <th>类型</th>
                    <th>当前规则</th>
                    <th>优先级</th>
                    <th>对照规则</th>
                    <th>对照优先级</th>
                    <th>交集场景</th>
                    <th>检测结论</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(conflict, index) in routeRuleConflicts" :key="`${conflict.conflictType}:${conflict.ruleCode}:${conflict.conflictingRuleCode || index}`">
                    <td><span :class="['badge', conflict.severityType]">{{ conflict.severity }}</span></td>
                    <td>{{ conflict.conflictType }}</td>
                    <td>{{ conflict.ruleCode }} / {{ conflict.ruleName }}</td>
                    <td>{{ conflict.priority }}</td>
                    <td>{{ conflict.conflictingRuleCode ? `${conflict.conflictingRuleCode} / ${conflict.conflictingRuleName}` : "-" }}</td>
                    <td>{{ conflict.conflictingPriority ?? "-" }}</td>
                    <td>{{ conflict.overlappingScene }}</td>
                    <td class="flow-summary-cell">{{ conflict.message }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div style="margin-top: 20px;">
            <div class="section-title">
              <div>
                <h3>路由规则复核申请</h3>
                <p class="meta">申请人和复核人均为自声明身份，不代表可信登录身份或正式签字审批；通过后才修改运行态</p>
              </div>
              <button class="button secondary" :disabled="isReviewLoading" @click="loadRouteRuleReviews">
                {{ isReviewLoading ? "刷新中..." : "刷新申请" }}
              </button>
            </div>
            <div class="notice-banner">
              新增、修改、启停和版本回滚均先生成候选申请，异人复核通过后才修改运行态。代码内复核通过不代表生产正式放行。
            </div>
            <div class="filter-grid" style="margin-bottom: 16px;">
              <label>
                操作申请人声明
                <input v-model.trim="routeRuleActionRequester" maxlength="64" placeholder="启停或回滚前必填" />
              </label>
              <label class="wide-field">
                操作申请原因
                <input v-model.trim="routeRuleActionReason" maxlength="255" placeholder="启停或回滚前必填" />
              </label>
              <label>
                复核人声明
                <input v-model.trim="reviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
              </label>
              <label class="wide-field">
                复核意见 / 驳回原因
                <input v-model.trim="reviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
              </label>
            </div>
            <div v-if="reviewErrorMessage" class="error-banner">
              {{ routeRuleReviews.length ? "路由规则复核申请刷新失败，当前展示上次成功快照：" : "路由规则复核申请加载失败：" }}{{ reviewErrorMessage }}
            </div>
            <div v-if="isReviewLoading" class="state-box">复核申请加载中...</div>
            <div v-else-if="!routeRuleReviews.length && !reviewErrorMessage" class="state-box">当前暂无路由规则复核申请</div>
            <div v-else-if="routeRuleReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>规则 / 操作</th>
                    <th>基线版本</th>
                    <th>候选配置</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in routeRuleReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>
                      {{ review.ruleCode }} / {{ review.ruleName }}
                      <div class="muted-text">{{ formatRouteRuleOperation(review.operationType) }}</div>
                    </td>
                    <td>
                      {{ review.baseVersionNo ? `V${review.baseVersionNo}` : "新规则" }}
                      <div v-if="review.sourceVersionNo" class="muted-text">
                        回滚目标 V{{ review.sourceVersionNo }}
                      </div>
                    </td>
                    <td class="flow-summary-cell">
                      {{ review.matchExpression }}
                      <div class="muted-text">
                        {{ review.targetChannelCode }} / 兜底 {{ review.fallbackChannelCode || "无" }} / 灰度 {{ review.rolloutPercentage ?? 100 }}% / 盐 {{ review.rolloutSalt }} / 优先级 {{ review.priority }} / {{ review.status }}
                      </div>
                    </td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleRouteRuleReview(review, true)"
                        >
                          通过并发布
                        </button>
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleRouteRuleReview(review, false)"
                        >
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div v-if="versionRuleCode" style="margin-top: 20px;">
            <div class="section-title">
              <div>
                <h3>{{ versionRuleCode }} 版本历史</h3>
                <p class="meta">历史快照不可修改；回滚先提交候选，复核通过后恢复配置并生成新版本</p>
              </div>
              <button class="button secondary" @click="closeRouteRuleVersions">关闭</button>
            </div>
            <div v-if="routeRuleVersionError" class="error-banner">
              {{ routeRuleVersions.length ? "规则版本刷新失败，当前展示上次成功快照：" : "规则版本加载失败：" }}{{ routeRuleVersionError }}
            </div>
            <div v-if="isVersionLoading" class="state-box">规则版本加载中...</div>
            <div v-else-if="!routeRuleVersions.length && !routeRuleVersionError" class="state-box">当前暂无版本记录</div>
            <div v-else-if="routeRuleVersions.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>版本</th>
                    <th>变更类型</th>
                    <th>来源版本</th>
                    <th>变更原因</th>
                    <th>匹配表达式</th>
                    <th>目标渠道</th>
                    <th>兜底渠道</th>
                    <th>灰度桶</th>
                    <th>优先级</th>
                    <th>状态</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="version in routeRuleVersions" :key="version.versionNo">
                    <td>V{{ version.versionNo }}</td>
                    <td>{{ version.changeType }}</td>
                    <td>{{ version.sourceVersionNo ? `V${version.sourceVersionNo}` : "-" }}</td>
                    <td class="flow-summary-cell">{{ version.changeReason }}</td>
                    <td class="flow-summary-cell">{{ version.matchExpression }}</td>
                    <td>{{ version.targetChannelCode }}</td>
                    <td>{{ version.fallbackChannelCode || "-" }}</td>
                    <td>{{ version.rolloutPercentage ?? 100 }}% / {{ version.rolloutSalt }}</td>
                    <td>{{ version.priority }}</td>
                    <td><span :class="['badge', version.statusType]">{{ version.status }}</span></td>
                    <td>{{ version.createdAt }}</td>
                    <td>
                      <button
                        class="link-button"
                        :disabled="Boolean(activeConfigCode)"
                        @click="rollbackRouteRuleVersion(version)"
                      >
                        申请回滚到此版本
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>支付协议管理</h3>
              <p class="meta">维护协议候选与不可变版本，异人复核通过后才更新运行态</p>
            </div>
            <div class="toolbar-actions">
              <button class="button secondary" @click="exportGovernanceSnapshot('PROTOCOLS')">导出协议</button>
              <button class="button secondary" @click="startCreateProtocol">新建协议</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ editingProtocolCode ? "编辑支付协议" : "新增支付协议" }}</h3>
                <p class="meta">按模板、渠道范围、商户确认和风控标签提交完整协议候选</p>
              </div>
            </div>

            <div class="filter-grid">
              <label>
                协议编码
                <input v-model.trim="protocolForm.protocolCode" :disabled="Boolean(editingProtocolCode)" />
              </label>
              <label>
                协议名称
                <input v-model.trim="protocolForm.protocolName" />
              </label>
              <label>
                协议类型
                <select v-model="protocolForm.protocolType">
                  <option value="">请选择协议类型</option>
                  <option v-for="option in protocolTypeOptions" :key="option.protocolType" :value="option.protocolType">
                    {{ option.protocolType }} / {{ option.protocolTypeName }}
                  </option>
                </select>
              </label>
              <label>
                模板编码
                <input v-model.trim="protocolForm.templateCode" placeholder="TPL_HK_SIGN_STD" />
              </label>
              <label>
                模板名称
                <input v-model.trim="protocolForm.templateName" placeholder="家政标准收单模板" />
              </label>
              <label>
                模板版本
                <input v-model.trim="protocolForm.templateVersion" placeholder="v1.0.0" />
              </label>
              <label>
                签约模式
                <input v-model.trim="protocolForm.signMode" />
              </label>
              <label>
                签约要素
                <input v-model.trim="protocolForm.signElementSpec" placeholder="姓名/身份证/手机号/授权确认" />
              </label>
              <label>
                电子签章
                <input v-model.trim="protocolForm.eSignatureProvider" placeholder="E-SIGN-CLOUD" />
              </label>
              <label>
                商户确认
                <select v-model="protocolForm.merchantAckRequired">
                  <option value="需要">需要</option>
                  <option value="不需要">不需要</option>
                </select>
              </label>
              <label>
                适用场景
                <input v-model.trim="protocolForm.sceneScope" placeholder="保洁/月嫂/到家服务" />
              </label>
              <label>
                适用渠道
                <input v-model.trim="protocolForm.channelScope" placeholder="wx_h5,alipay_h5" />
              </label>
              <label>
                风控标签
                <input v-model.trim="protocolForm.riskControlTag" placeholder="实名+重复签约校验" />
              </label>
              <label class="wide-field">
                协议正文
                <textarea
                  v-model.trim="protocolForm.protocolBody"
                  rows="6"
                  placeholder="请输入协议正文，建议按条款分段维护"
                />
              </label>
              <label>
                优先级
                <input v-model.number="protocolForm.priority" type="number" min="0" />
              </label>
              <label>
                启用状态
                <select v-model="protocolForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
              <label class="wide-field">
                申请原因
                <input v-model.trim="protocolForm.changeReason" maxlength="255" placeholder="新增或编辑协议复核时必填" />
              </label>
              <label>
                申请人声明
                <input v-model.trim="protocolForm.requester" maxlength="64" placeholder="当前为自声明身份" />
              </label>
            </div>

            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button
                class="button primary"
                :disabled="activeConfigCode === (editingProtocolCode || protocolForm.protocolCode)"
                @click="submitProtocolForm"
              >
                {{ editingProtocolCode ? "提交修改复核" : "提交新增复核" }}
              </button>
              <button class="button secondary" @click="startCreateProtocol">重置表单</button>
            </div>
          </div>

          <div class="filter-grid" style="margin-bottom: 16px;">
            <label>
              启停 / 回滚申请人
              <input v-model.trim="protocolActionRequester" maxlength="64" placeholder="当前为自声明身份" />
            </label>
            <label class="wide-field">
              启停 / 回滚申请原因
              <input v-model.trim="protocolActionReason" maxlength="255" placeholder="提交启停或回滚复核前必填" />
            </label>
            <label>
              复核人声明
              <input v-model.trim="protocolReviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
            </label>
            <label class="wide-field">
              复核意见 / 驳回原因
              <input v-model.trim="protocolReviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
            </label>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>协议编码</th>
                  <th>协议名称</th>
                  <th>协议类型</th>
                  <th>类型名称</th>
                  <th>模板编码</th>
                  <th>模板名称</th>
                  <th>模板版本</th>
                  <th>签约模式</th>
                  <th>签约要素</th>
                  <th>电子签章</th>
                  <th>适用场景</th>
                  <th>适用渠道</th>
                  <th>商户确认</th>
                  <th>风控标签</th>
                  <th>协议正文</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="protocol in protocols" :key="protocol.protocolCode">
                  <td>{{ protocol.protocolCode }}</td>
                  <td>{{ protocol.protocolName }}</td>
                  <td>{{ protocol.protocolType }}</td>
                  <td>{{ protocol.protocolTypeName }}</td>
                  <td>{{ protocol.templateCode }}</td>
                  <td>{{ protocol.templateName }}</td>
                  <td>{{ protocol.templateVersion }}</td>
                  <td>{{ protocol.signMode }}</td>
                  <td class="flow-summary-cell">{{ protocol.signElementSpec }}</td>
                  <td>{{ protocol.eSignatureProvider }}</td>
                  <td>{{ protocol.sceneScope }}</td>
                  <td>{{ protocol.channelScope }}</td>
                  <td>{{ protocol.merchantAckRequired }}</td>
                  <td class="flow-summary-cell">{{ protocol.riskControlTag }}</td>
                  <td class="flow-summary-cell">{{ protocol.protocolBody }}</td>
                  <td>{{ protocol.priority }}</td>
                  <td><span :class="['badge', protocol.statusType]">{{ protocol.status }}</span></td>
                  <td>{{ protocol.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === protocol.protocolCode"
                      @click="startEditProtocol(protocol)"
                    >
                      编辑
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === protocol.protocolCode"
                      @click="toggleProtocol(protocol)"
                    >
                      {{ protocol.status === "ENABLED" ? "停用" : "启用" }}
                    </button>
                    <button
                      class="link-button"
                      :disabled="Boolean(activeConfigCode)"
                      @click="loadProtocolVersions(protocol.protocolCode)"
                    >
                      版本
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div style="margin-top: 20px;">
            <div class="section-title">
              <div>
                <h3>支付协议复核申请</h3>
                <p class="meta">申请人与复核人均为自声明身份，不代表可信登录、法务审批或正式签字</p>
              </div>
              <button class="button secondary" :disabled="isProtocolReviewLoading" @click="loadProtocolReviews">
                {{ isProtocolReviewLoading ? "刷新中..." : "刷新申请" }}
              </button>
            </div>
            <div class="notice-banner">
              新增、修改、启停和回滚均先生成完整候选，异人复核通过后才更新运行态并追加版本。
            </div>
            <div v-if="protocolReviewErrorMessage" class="error-banner">
              {{ protocolReviews.length ? "协议复核申请刷新失败，当前展示上次成功快照：" : "协议复核申请加载失败：" }}{{ protocolReviewErrorMessage }}
            </div>
            <div v-if="isProtocolReviewLoading" class="state-box">协议复核申请加载中...</div>
            <div v-else-if="!protocolReviews.length && !protocolReviewErrorMessage" class="state-box">当前暂无支付协议复核申请</div>
            <div v-else-if="protocolReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>协议 / 操作</th>
                    <th>基线版本</th>
                    <th>候选模板</th>
                    <th>候选范围与状态</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in protocolReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>
                      {{ review.protocolCode }} / {{ review.protocolName }}
                      <div class="muted-text">{{ formatProtocolOperation(review.operationType) }}</div>
                    </td>
                    <td>
                      {{ review.baseVersionNo ? `V${review.baseVersionNo}` : "新协议" }}
                      <div v-if="review.sourceVersionNo" class="muted-text">
                        回滚目标 V{{ review.sourceVersionNo }}
                      </div>
                    </td>
                    <td>
                      {{ review.templateCode }} / {{ review.templateVersion }}
                      <div class="muted-text">{{ review.signMode }} / {{ review.eSignatureProvider }}</div>
                    </td>
                    <td class="flow-summary-cell">
                      {{ review.sceneScope }} / {{ review.channelScope }}
                      <div class="muted-text">{{ review.status }} / 优先级 {{ review.priority }}</div>
                    </td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleProtocolReview(review, true)"
                        >
                          通过并发布
                        </button>
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleProtocolReview(review, false)"
                        >
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div v-if="protocolVersionCode" class="panel" style="margin-top: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ protocolVersionCode }} 版本历史</h3>
                <p class="meta">历史快照不可修改；回滚先生成候选，异人复核通过后才恢复配置并追加新版本</p>
              </div>
              <button class="button secondary" @click="closeProtocolVersions">关闭</button>
            </div>
            <div v-if="protocolVersionError" class="error-banner">
              {{ protocolVersions.length ? "协议版本刷新失败，当前展示上次成功快照：" : "协议版本加载失败：" }}{{ protocolVersionError }}
            </div>
            <div v-if="isProtocolVersionLoading" class="state-box">协议版本加载中...</div>
            <div v-else-if="!protocolVersions.length && !protocolVersionError" class="state-box">当前暂无协议版本记录</div>
            <div v-else-if="protocolVersions.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>版本</th>
                    <th>变更类型</th>
                    <th>来源版本</th>
                    <th>模板</th>
                    <th>适用范围</th>
                    <th>协议正文</th>
                    <th>状态</th>
                    <th>变更原因</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="version in protocolVersions" :key="version.versionNo">
                    <td>V{{ version.versionNo }}</td>
                    <td>{{ version.changeType }}</td>
                    <td>{{ version.sourceVersionNo ? `V${version.sourceVersionNo}` : "-" }}</td>
                    <td>{{ version.templateCode }} / {{ version.templateVersion }}</td>
                    <td class="flow-summary-cell">{{ version.sceneScope }} / {{ version.channelScope }}</td>
                    <td class="flow-summary-cell">{{ version.protocolBody }}</td>
                    <td><span :class="['badge', version.statusType]">{{ version.status }}</span></td>
                    <td class="flow-summary-cell">{{ version.changeReason }}</td>
                    <td>{{ version.createdAt }}</td>
                    <td>
                      <button
                        class="link-button"
                        :disabled="Boolean(activeConfigCode) || version.versionNo === protocolVersions[0]?.versionNo"
                        @click="rollbackProtocolVersion(version)"
                      >
                        回滚到此版本
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>渠道返回码映射</h3>
              <p class="meta">统一微信、支付宝、银行通道的错误码口径，沉淀标准化错误文案与处理建议</p>
            </div>
            <div class="table-inline-actions">
              <button class="button secondary" @click="downloadReturnCodeImportTemplate">下载导入模板</button>
              <button class="button secondary" @click="exportGovernanceSnapshot('RETURN_CODES')">导出返回码</button>
            </div>
          </div>

          <div class="return-code-import-panel">
            <div class="filter-grid">
              <label>
                CSV 文件
                <input ref="returnCodeImportFileInput" type="file" accept=".csv,text/csv" @change="handleReturnCodeImportFile" />
                <span v-if="returnCodeImportFileName" class="meta">{{ returnCodeImportFileName }}</span>
              </label>
              <label>
                申请人声明
                <input v-model.trim="returnCodeImportRequester" maxlength="64" placeholder="请输入申请人" />
              </label>
              <label class="wide-field">
                导入原因
                <input v-model.trim="returnCodeImportReason" maxlength="256" placeholder="请输入本次导入原因" />
              </label>
            </div>
            <div class="table-inline-actions">
              <button
                class="button secondary"
                :disabled="!returnCodeImportCsv || isReturnCodeImportPreviewing || isReturnCodeImportSubmitting"
                @click="previewReturnCodeImport"
              >
                {{ isReturnCodeImportPreviewing ? "预检中" : "差异预检" }}
              </button>
              <button
                class="button"
                :disabled="!returnCodeImportPreview?.valid || isReturnCodeImportSubmitting || isReturnCodeImportPreviewing"
                @click="submitReturnCodeImportReview"
              >
                {{ isReturnCodeImportSubmitting ? "提交中" : "提交复核" }}
              </button>
            </div>
            <template v-if="returnCodeImportPreview">
              <div class="import-summary" aria-live="polite">
                <span>总计 {{ returnCodeImportPreview.totalCount }}</span>
                <span>新增 {{ returnCodeImportPreview.createCount }}</span>
                <span>更新 {{ returnCodeImportPreview.updateCount }}</span>
                <span>不变 {{ returnCodeImportPreview.unchangedCount }}</span>
                <span :class="{ 'import-invalid': returnCodeImportPreview.invalidCount > 0 }">
                  无效 {{ returnCodeImportPreview.invalidCount }}
                </span>
                <span v-if="returnCodeImportPreview.batchNo">批次 {{ returnCodeImportPreview.batchNo }}</span>
              </div>
              <div class="table-wrap import-preview-table">
                <table>
                  <thead>
                    <tr>
                      <th>行号</th>
                      <th>动作</th>
                      <th>渠道 / 返回码</th>
                      <th>标准码</th>
                      <th>映射版本</th>
                      <th>差异或问题</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in returnCodeImportPreview.items" :key="`${item.rowNumber}-${item.candidate.channelCode}-${item.candidate.channelReturnCode}`">
                      <td>{{ item.rowNumber }}</td>
                      <td><span :class="['badge', item.valid ? (item.action === 'UNCHANGED' ? 'info' : 'success') : 'danger']">{{ formatReturnCodeImportAction(item.action) }}</span></td>
                      <td>{{ item.candidate.channelCode }} / {{ item.candidate.channelReturnCode }}</td>
                      <td>{{ item.candidate.standardizedCode }}</td>
                      <td>{{ item.candidate.mappingVersion }}</td>
                      <td class="flow-summary-cell">{{ item.message }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </div>

          <div class="return-code-import-panel">
            <div class="section-title">
              <div>
                <h3>返回码导入复核申请</h3>
                <p class="meta">申请人与复核人均为自声明身份；通过前重新校验基线，驳回不改变运行态</p>
              </div>
              <button
                class="button secondary"
                :disabled="isReturnCodeImportReviewLoading"
                @click="loadReturnCodeImportReviews"
              >
                {{ isReturnCodeImportReviewLoading ? "刷新中..." : "刷新申请" }}
              </button>
            </div>
            <div class="notice-banner">
              CSV 候选提交后不会直接生效，必须由不同的复核人处理；自声明异人约束不代表可信身份或正式审批。
            </div>
            <div class="filter-grid" style="margin-bottom: 16px;">
              <label>
                复核人声明
                <input v-model.trim="returnCodeImportReviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
              </label>
              <label class="wide-field">
                复核意见 / 驳回原因
                <input v-model.trim="returnCodeImportReviewComment" maxlength="256" placeholder="处理待复核申请时必填" />
              </label>
            </div>
            <div v-if="returnCodeImportReviewError" class="error-banner">
              {{ returnCodeImportReviews.length ? "返回码导入复核申请刷新失败，当前展示上次成功快照：" : "返回码导入复核申请加载失败：" }}{{ returnCodeImportReviewError }}
            </div>
            <div v-if="isReturnCodeImportReviewLoading" class="state-box">返回码导入复核申请加载中...</div>
            <div v-else-if="!returnCodeImportReviews.length && !returnCodeImportReviewError" class="state-box">当前暂无返回码导入复核申请</div>
            <div v-else-if="returnCodeImportReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>差异摘要</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>审计批次</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in returnCodeImportReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>
                      总计 {{ review.totalCount }} / 新增 {{ review.createCount }} / 更新 {{ review.updateCount }}
                      <div class="muted-text">不变 {{ review.unchangedCount }} / SHA {{ review.sourceSha256?.slice(0, 12) }}</div>
                    </td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>{{ review.appliedBatchNo || "-" }}</td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleReturnCodeImportReview(review, true)"
                        >
                          通过并应用
                        </button>
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleReturnCodeImportReview(review, false)"
                        >
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>渠道编码</th>
                  <th>渠道返回码</th>
                  <th>标准错误码</th>
                  <th>标准错误文案</th>
                  <th>处理建议</th>
                  <th>可重试</th>
                  <th>人工介入</th>
                  <th>映射版本</th>
                  <th>归档状态</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="mapping in returnCodeMappings" :key="`${mapping.channelCode}-${mapping.channelReturnCode}`">
                  <td>{{ mapping.channelCode }}</td>
                  <td>{{ mapping.channelReturnCode }}</td>
                  <td>{{ mapping.standardizedCode }}</td>
                  <td>{{ mapping.standardizedMessage }}</td>
                  <td class="flow-summary-cell">{{ mapping.handlingSuggestion }}</td>
                  <td>{{ mapping.retryable }}</td>
                  <td>{{ mapping.manualInterventionRequired }}</td>
                  <td>{{ mapping.mappingVersion }}</td>
                  <td><span :class="['badge', mapping.archiveStatusType]">{{ mapping.archiveStatus }}</span></td>
                  <td><span :class="['badge', mapping.statusType]">{{ mapping.status }}</span></td>
                  <td>{{ mapping.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === `${mapping.channelCode}:${mapping.channelReturnCode}`"
                      @click="toggleReturnCodeMapping(mapping)"
                    >
                      {{ mapping.status === "ENABLED" ? "申请停用" : "申请启用" }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>支付网关接入管理</h3>
              <p class="meta">统一维护渠道接入模式、协议算法、超时重试和网关基础地址，支撑真实渠道适配器正式化</p>
            </div>
            <div class="toolbar-actions">
              <button class="button secondary" @click="exportGovernanceSnapshot('GATEWAYS')">导出网关</button>
              <button class="button secondary" @click="startCreateGateway">新增网关</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ editingGatewayCode ? "编辑支付网关" : "新增支付网关" }}</h3>
                <p class="meta">完整候选经异人声明复核通过后才写入运行态；本表单不执行证书上传、探活或灰度发布动作</p>
              </div>
            </div>
            <div class="filter-grid">
              <label>
                网关编码
                <input v-model.trim="gatewayForm.gatewayCode" :disabled="Boolean(editingGatewayCode)" maxlength="64" placeholder="GATEWAY_WX_ACQ" />
              </label>
              <label>
                网关名称
                <input v-model.trim="gatewayForm.gatewayName" maxlength="128" />
              </label>
              <label>
                接入模式
                <input v-model.trim="gatewayForm.accessMode" maxlength="64" placeholder="直连 / 服务商模式" />
              </label>
              <label>
                适用渠道
                <input v-model.trim="gatewayForm.channelScope" maxlength="256" placeholder="wx_h5,alipay_h5" />
              </label>
              <label>
                环境范围
                <input v-model.trim="gatewayForm.environmentScope" maxlength="128" placeholder="生产+预发" />
              </label>
              <label class="wide-field">
                网关基础地址
                <input v-model.trim="gatewayForm.apiBaseUrl" maxlength="256" placeholder="https://..." />
              </label>
              <label>
                报文协议
                <input v-model.trim="gatewayForm.protocolType" maxlength="64" placeholder="HTTP+JSON" />
              </label>
              <label>
                签名算法
                <input v-model.trim="gatewayForm.signAlgorithm" maxlength="64" placeholder="HMAC-SHA256 / RSA2" />
              </label>
              <label>
                证书别名
                <input v-model.trim="gatewayForm.certificateAlias" maxlength="128" />
              </label>
              <label>
                证书状态
                <select v-model="gatewayForm.certificateStatus">
                  <option value="VALID">VALID</option>
                  <option value="EXPIRING_SOON">EXPIRING_SOON</option>
                  <option value="EXPIRED">EXPIRED</option>
                  <option value="ARCHIVED">ARCHIVED</option>
                </select>
              </label>
              <label>
                发布阶段
                <input v-model.trim="gatewayForm.releaseStage" maxlength="64" placeholder="灰度 / 全量 / 只读演练" />
              </label>
              <label class="wide-field">
                灰度策略
                <input v-model.trim="gatewayForm.grayStrategy" maxlength="256" />
              </label>
              <label class="wide-field">
                回调白名单
                <input v-model.trim="gatewayForm.callbackWhitelist" maxlength="256" placeholder="来源网段摘要" />
              </label>
              <label class="wide-field">
                适配器编排
                <input v-model.trim="gatewayForm.adapterRegistry" maxlength="256" placeholder="submit=...,query=...,refund=..." />
              </label>
              <label>
                超时时间（毫秒）
                <input v-model.number="gatewayForm.timeoutMs" type="number" min="1" />
              </label>
              <label>
                重试策略
                <input v-model.trim="gatewayForm.retryPolicy" maxlength="128" />
              </label>
              <label>
                优先级
                <input v-model.number="gatewayForm.priority" type="number" min="0" />
              </label>
              <label>
                启用状态
                <select v-model="gatewayForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
              <label>
                申请人声明
                <input v-model.trim="gatewayForm.requester" maxlength="64" />
              </label>
              <label class="wide-field">
                申请原因
                <input v-model.trim="gatewayForm.changeReason" maxlength="255" />
              </label>
            </div>
            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button class="button primary" :disabled="activeConfigCode === (editingGatewayCode || gatewayForm.gatewayCode)" @click="submitGatewayForm">
                {{ editingGatewayCode ? "提交修改复核" : "提交新增复核" }}
              </button>
              <button class="button secondary" @click="startCreateGateway">重置表单</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>支付网关配置变更复核</h3>
                <p class="meta">新增、编辑和启停先提交完整候选，异人声明批准且网关基线有效后才更新运行态</p>
              </div>
              <button class="button secondary" :disabled="isGatewayReviewLoading" @click="loadGatewayReviews">
                {{ isGatewayReviewLoading ? "加载中..." : "刷新申请" }}
              </button>
            </div>
            <div class="filter-grid">
              <label>
                申请人声明
                <input v-model.trim="gatewayActionRequester" maxlength="64" placeholder="提交启停申请时必填" />
              </label>
              <label>
                申请原因
                <input v-model.trim="gatewayActionReason" maxlength="255" placeholder="提交启停申请时必填" />
              </label>
              <label>
                复核人声明
                <input v-model.trim="gatewayReviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
              </label>
              <label>
                复核意见 / 驳回原因
                <input v-model.trim="gatewayReviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
              </label>
            </div>
            <div v-if="gatewayReviewError" class="error-banner">
              {{ gatewayReviews.length ? "支付网关复核申请刷新失败，当前展示上次成功快照：" : "支付网关复核申请加载失败：" }}{{ gatewayReviewError }}
            </div>
            <div v-if="isGatewayReviewLoading" class="state-box">支付网关复核申请加载中...</div>
            <div v-else-if="!gatewayReviews.length && !gatewayReviewError" class="state-box">当前暂无支付网关配置复核申请</div>
            <div v-else-if="gatewayReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>操作</th>
                    <th>网关</th>
                    <th>候选状态</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in gatewayReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>{{ review.operationType }}</td>
                    <td>
                      {{ review.gatewayCode }} / {{ review.gatewayName }}
                      <div class="muted-text">{{ review.accessMode }} / {{ review.channelScope }} / {{ review.protocolType }}</div>
                      <div class="muted-text">{{ review.certificateStatus }} / {{ review.releaseStage }} / {{ review.timeoutMs }}ms</div>
                      <div class="muted-text">
                        基线 {{ review.baseFingerprint?.slice(0, 12) || "新增" }}
                        / V{{ review.baseVersionNo || "-" }}
                        <template v-if="review.sourceVersionNo"> / 来源 V{{ review.sourceVersionNo }}</template>
                      </div>
                    </td>
                    <td><span :class="['badge', review.candidateStatusType]">{{ review.candidateStatus }}</span></td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleGatewayReview(review, true)"
                        >
                          通过并应用
                        </button>
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleGatewayReview(review, false)"
                        >
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>网关编码</th>
                  <th>网关名称</th>
                  <th>接入模式</th>
                  <th>适用渠道</th>
                  <th>环境范围</th>
                  <th>基础地址</th>
                  <th>报文协议</th>
                  <th>签名算法</th>
                  <th>证书别名</th>
                  <th>证书状态</th>
                  <th>发布阶段</th>
                  <th>灰度策略</th>
                  <th>回调白名单</th>
                  <th>适配器编排</th>
                  <th>超时时间</th>
                  <th>重试策略</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="gateway in gateways" :key="gateway.gatewayCode">
                  <td>{{ gateway.gatewayCode }}</td>
                  <td>{{ gateway.gatewayName }}</td>
                  <td>{{ gateway.accessMode }}</td>
                  <td>{{ gateway.channelScope }}</td>
                  <td>{{ gateway.environmentScope }}</td>
                  <td class="flow-summary-cell">{{ gateway.apiBaseUrl }}</td>
                  <td>{{ gateway.protocolType }}</td>
                  <td>{{ gateway.signAlgorithm }}</td>
                  <td>{{ gateway.certificateAlias }}</td>
                  <td><span :class="['badge', gateway.certificateStatusType]">{{ gateway.certificateStatus }}</span></td>
                  <td>{{ gateway.releaseStage }}</td>
                  <td class="flow-summary-cell">{{ gateway.grayStrategy }}</td>
                  <td class="flow-summary-cell">{{ gateway.callbackWhitelist }}</td>
                  <td class="flow-summary-cell">{{ gateway.adapterRegistry }}</td>
                  <td>{{ gateway.timeoutMs }}</td>
                  <td class="flow-summary-cell">{{ gateway.retryPolicy }}</td>
                  <td>{{ gateway.priority }}</td>
                  <td><span :class="['badge', gateway.statusType]">{{ gateway.status }}</span></td>
                  <td>{{ gateway.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === gateway.gatewayCode"
                      @click="startEditGateway(gateway)"
                    >
                      编辑
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === gateway.gatewayCode"
                      @click="toggleGateway(gateway)"
                    >
                      {{ gateway.status === "ENABLED" ? "申请停用" : "申请启用" }}
                    </button>
                    <button
                      class="link-button"
                      :disabled="Boolean(activeConfigCode)"
                      @click="loadGatewayVersions(gateway.gatewayCode)"
                    >
                      版本历史
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="gatewayVersionCode" class="panel" style="margin-top: 16px;">
            <div class="section-title">
              <div>
                <h3>支付网关版本历史 / {{ gatewayVersionCode }}</h3>
                <p class="meta">历史快照不可变；回滚需重新提交完整候选并经异人声明复核，当前运行态不会立即改变</p>
              </div>
              <button class="button secondary" @click="closeGatewayVersions">关闭</button>
            </div>
            <div v-if="gatewayVersionError" class="error-banner">
              {{ gatewayVersions.length ? "支付网关版本刷新失败，当前展示上次成功快照：" : "支付网关版本加载失败：" }}{{ gatewayVersionError }}
            </div>
            <div v-if="isGatewayVersionLoading" class="state-box">支付网关版本加载中...</div>
            <div v-else-if="!gatewayVersions.length && !gatewayVersionError" class="state-box">当前暂无支付网关版本记录</div>
            <div v-else-if="gatewayVersions.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>版本</th>
                    <th>变更类型</th>
                    <th>配置摘要</th>
                    <th>证书 / 发布</th>
                    <th>运行参数</th>
                    <th>状态</th>
                    <th>变更原因</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="version in gatewayVersions" :key="version.versionNo">
                    <td>V{{ version.versionNo }}</td>
                    <td>
                      {{ version.changeType }}
                      <div v-if="version.sourceVersionNo" class="muted-text">来源 V{{ version.sourceVersionNo }}</div>
                    </td>
                    <td>
                      {{ version.gatewayName }} / {{ version.accessMode }}
                      <div class="muted-text">{{ version.channelScope }} / {{ version.environmentScope }}</div>
                      <div class="muted-text">{{ version.protocolType }} / {{ version.signAlgorithm }}</div>
                    </td>
                    <td>
                      {{ version.certificateAlias }}
                      <div class="muted-text">{{ version.certificateStatus }} / {{ version.releaseStage }}</div>
                    </td>
                    <td>
                      {{ version.timeoutMs }}ms / 优先级 {{ version.priority }}
                      <div class="muted-text">{{ version.retryPolicy }}</div>
                    </td>
                    <td><span :class="['badge', version.statusType]">{{ version.status }}</span></td>
                    <td>{{ version.changeReason }}</td>
                    <td>{{ version.createdAt }}</td>
                    <td>
                      <button
                        class="link-button"
                        :disabled="Boolean(activeConfigCode) || version.versionNo === gatewayVersions[0]?.versionNo"
                        @click="rollbackGatewayVersion(version)"
                      >
                        提交回滚复核
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>支付控制策略</h3>
              <p class="meta">统一维护来源应用的支付方式权限、渠道权限、分钟级限流与自检准入，控制正向支付主链路风险</p>
            </div>
            <div class="toolbar-actions">
              <button class="button secondary" @click="exportGovernanceSnapshot('CONTROL_POLICIES')">导出策略</button>
              <button class="button secondary" @click="startCreateControlPolicy">新增策略</button>
            </div>
          </div>

          <div style="margin-bottom: 20px;">
            <div class="section-title">
              <div>
                <h3>{{ editingControlPolicyId ? "编辑支付控制策略" : "新增支付控制策略" }}</h3>
                <p class="meta">完整候选经异人声明复核通过后才写入运行态；编辑时访问令牌留空表示保持现值</p>
              </div>
            </div>
            <div class="filter-grid">
              <label>
                来源应用标识
                <input v-model.trim="controlPolicyForm.sourceAppId" :disabled="Boolean(editingControlPolicyId)" maxlength="64" placeholder="housekeeping-app-web" />
              </label>
              <label>
                来源应用名称
                <input v-model.trim="controlPolicyForm.sourceAppName" maxlength="128" />
              </label>
              <label class="wide-field">
                允许支付方式
                <input v-model.trim="controlPolicyForm.allowedPaymentMethods" maxlength="255" placeholder="微信支付,支付宝" />
              </label>
              <label class="wide-field">
                允许渠道
                <input v-model.trim="controlPolicyForm.allowedChannelCodes" maxlength="255" placeholder="wx_h5,alipay_h5" />
              </label>
              <label class="wide-field">
                允许商户号
                <input v-model.trim="controlPolicyForm.allowedMerchantNos" maxlength="255" placeholder="MCH_WX_10001,MCH_ALI_20001" />
              </label>
              <label>
                分钟级提交限流
                <input v-model.number="controlPolicyForm.minuteSubmitLimit" type="number" min="1" />
              </label>
              <label>
                接口级分钟限流
                <input v-model.number="controlPolicyForm.interfaceMinuteSubmitLimit" type="number" min="1" />
              </label>
              <label>
                令牌鉴权
                <select v-model="controlPolicyForm.tokenAuthRequired">
                  <option :value="true">开启</option>
                  <option :value="false">关闭</option>
                </select>
              </label>
              <label class="wide-field">
                接口访问令牌
                <input v-model.trim="controlPolicyForm.accessTokenValue" type="password" maxlength="128" autocomplete="new-password" />
              </label>
              <label>
                严格模式
                <select v-model="controlPolicyForm.strictMode">
                  <option :value="true">开启</option>
                  <option :value="false">关闭</option>
                </select>
              </label>
              <label>
                启用状态
                <select v-model="controlPolicyForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
              <label>
                申请人声明
                <input v-model.trim="controlPolicyForm.requester" maxlength="64" />
              </label>
              <label class="wide-field">
                申请原因
                <input v-model.trim="controlPolicyForm.changeReason" maxlength="255" />
              </label>
            </div>
            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button class="button primary" :disabled="activeConfigCode === (editingControlPolicyId || controlPolicyForm.sourceAppId)" @click="submitControlPolicyForm">
                {{ editingControlPolicyId ? "提交修改复核" : "提交新增复核" }}
              </button>
              <button class="button secondary" @click="startCreateControlPolicy">重置表单</button>
            </div>
          </div>

          <div style="margin-bottom: 20px;">
            <div class="section-title">
              <div>
                <h3>支付控制策略配置变更复核</h3>
                <p class="meta">新增、编辑、启停和历史回滚共享唯一待办；批准后追加不可变版本并使旧自检结论失效</p>
              </div>
              <button class="button secondary" :disabled="isControlPolicyReviewLoading" @click="loadControlPolicyReviews">
                {{ isControlPolicyReviewLoading ? "加载中..." : "刷新申请" }}
              </button>
            </div>
            <div class="notice-box" style="margin-bottom: 16px;">
              申请人与复核人均为自声明且必须不同；列表只展示令牌是否配置，不返回令牌原文。
            </div>
            <div class="filter-grid" style="margin-bottom: 16px;">
              <label>
                申请人声明
                <input v-model.trim="controlPolicyActionRequester" maxlength="64" placeholder="提交启停或回滚时必填" />
              </label>
              <label>
                申请原因
                <input v-model.trim="controlPolicyActionReason" maxlength="255" placeholder="提交启停或回滚时必填" />
              </label>
              <label>
                复核人声明
                <input v-model.trim="controlPolicyReviewer" maxlength="64" placeholder="必须与申请人不同" />
              </label>
              <label class="wide-field">
                复核意见 / 驳回原因
                <input v-model.trim="controlPolicyReviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
              </label>
            </div>
            <div v-if="controlPolicyReviewError" class="error-banner">
              {{ controlPolicyReviews.length ? "支付控制策略复核申请刷新失败，当前展示上次成功快照：" : "支付控制策略复核申请加载失败：" }}{{ controlPolicyReviewError }}
            </div>
            <div v-if="isControlPolicyReviewLoading" class="state-box">支付控制策略复核申请加载中...</div>
            <div v-else-if="!controlPolicyReviews.length && !controlPolicyReviewError" class="state-box">当前暂无支付控制策略配置复核申请</div>
            <div v-else-if="controlPolicyReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>操作</th>
                    <th>来源应用</th>
                    <th>授权范围</th>
                    <th>限流 / 安全</th>
                    <th>候选状态</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>处理</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in controlPolicyReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>{{ review.operationType }}</td>
                    <td>
                      {{ review.sourceAppId }}
                      <div class="muted-text">{{ review.sourceAppName }}</div>
                      <div class="muted-text">基线 {{ review.baseVersionNo ? `V${review.baseVersionNo}` : "新增" }} / {{ review.baseFingerprint?.slice(0, 12) || "-" }}</div>
                      <div v-if="review.sourceVersionNo" class="muted-text">回滚来源 V{{ review.sourceVersionNo }}</div>
                    </td>
                    <td class="flow-summary-cell">
                      支付方式 {{ review.allowedPaymentMethods }}<br />
                      渠道 {{ review.allowedChannelCodes }}<br />
                      商户 {{ review.allowedMerchantNos }}
                    </td>
                    <td>
                      {{ review.minuteSubmitLimit }} / {{ review.interfaceMinuteSubmitLimit }}<br />
                      令牌鉴权 {{ review.tokenAuthRequired }} / {{ review.accessTokenConfigured ? "已配置" : "未配置" }}<br />
                      严格模式 {{ review.strictMode }}
                    </td>
                    <td><span :class="['badge', review.candidateStatusType]">{{ review.candidateStatus }}</span></td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }}</div>
                      <div class="muted-text">{{ review.createdAt }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td>
                      <span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span>
                      <div class="muted-text">{{ review.reviewer || "待复核" }} / {{ review.reviewComment || "暂无意见" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button class="link-button" :disabled="activeConfigCode === `control-policy-review:${review.requestNo}`" @click="handleControlPolicyReview(review, true)">通过</button>
                        <button class="link-button" :disabled="activeConfigCode === `control-policy-review:${review.requestNo}`" @click="handleControlPolicyReview(review, false)">驳回</button>
                      </template>
                      <span v-else class="muted-text">{{ review.reviewedAt || "已处理" }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>来源应用标识</th>
                  <th>来源应用名称</th>
                  <th>允许支付方式</th>
                  <th>允许渠道</th>
                  <th>允许商户号</th>
                  <th>分钟限流</th>
                  <th>接口限流</th>
                  <th>令牌鉴权</th>
                  <th>令牌状态</th>
                  <th>严格模式</th>
                  <th>自检状态</th>
                  <th>自检提示</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="policy in controlPolicies" :key="policy.sourceAppId">
                  <td>{{ policy.sourceAppId }}</td>
                  <td>{{ policy.sourceAppName }}</td>
                  <td class="flow-summary-cell">{{ policy.allowedPaymentMethods }}</td>
                  <td class="flow-summary-cell">{{ policy.allowedChannelCodes }}</td>
                  <td class="flow-summary-cell">{{ policy.allowedMerchantNos }}</td>
                  <td>{{ policy.minuteSubmitLimit }}</td>
                  <td>{{ policy.interfaceMinuteSubmitLimit }}</td>
                  <td>{{ policy.tokenAuthRequired }}</td>
                  <td>{{ policy.accessTokenConfigured ? "已配置" : "未配置" }}</td>
                  <td>{{ policy.strictMode }}</td>
                  <td><span :class="['badge', policy.selfCheckStatusType]">{{ policy.selfCheckStatus }}</span></td>
                  <td class="flow-summary-cell">{{ policy.selfCheckMessage }}</td>
                  <td><span :class="['badge', policy.statusType]">{{ policy.status }}</span></td>
                  <td>{{ policy.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === policy.sourceAppId"
                      @click="startEditControlPolicy(policy)"
                    >
                      编辑
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === policy.sourceAppId"
                      @click="submitControlPolicyStatusReview(policy)"
                    >
                      {{ policy.status === "ENABLED" ? "申请停用" : "申请启用" }}
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === policy.sourceAppId"
                      @click="runControlPolicySelfCheck(policy)"
                    >
                      执行自检
                    </button>
                    <button
                      class="link-button"
                      :disabled="Boolean(activeConfigCode)"
                      @click="loadControlPolicyVersions(policy.sourceAppId)"
                    >
                      版本历史
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="controlPolicyVersionSourceAppId" class="panel" style="margin-top: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ controlPolicyVersionSourceAppId }} 版本历史</h3>
                <p class="meta">历史快照不可修改且不展示令牌原文；回滚复核通过后恢复完整配置、重置自检并追加新版本</p>
              </div>
              <button class="button secondary" @click="closeControlPolicyVersions">关闭</button>
            </div>
            <div v-if="controlPolicyVersionError" class="error-banner">
              {{ controlPolicyVersions.length ? "控制策略版本刷新失败，当前展示上次成功快照：" : "控制策略版本加载失败：" }}{{ controlPolicyVersionError }}
            </div>
            <div v-if="isControlPolicyVersionLoading" class="state-box">控制策略版本加载中...</div>
            <div v-else-if="!controlPolicyVersions.length && !controlPolicyVersionError" class="state-box">当前暂无控制策略版本记录</div>
            <div v-else-if="controlPolicyVersions.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>版本</th>
                    <th>变更类型</th>
                    <th>来源版本</th>
                    <th>授权范围</th>
                    <th>限流 / 安全</th>
                    <th>状态</th>
                    <th>变更原因</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="version in controlPolicyVersions" :key="version.versionNo">
                    <td>V{{ version.versionNo }}</td>
                    <td>{{ version.changeType }}</td>
                    <td>{{ version.sourceVersionNo ? `V${version.sourceVersionNo}` : "-" }}</td>
                    <td class="flow-summary-cell">
                      支付方式 {{ version.allowedPaymentMethods }}<br />
                      渠道 {{ version.allowedChannelCodes }}<br />
                      商户 {{ version.allowedMerchantNos }}
                    </td>
                    <td>
                      {{ version.minuteSubmitLimit }} / {{ version.interfaceMinuteSubmitLimit }}<br />
                      令牌鉴权 {{ version.tokenAuthRequired }} / {{ version.accessTokenConfigured ? "已配置" : "未配置" }}<br />
                      严格模式 {{ version.strictMode }}
                    </td>
                    <td><span :class="['badge', version.statusType]">{{ version.status }}</span></td>
                    <td class="flow-summary-cell">{{ version.changeReason }}</td>
                    <td>{{ version.createdAt }}</td>
                    <td>
                      <button
                        class="link-button"
                        :disabled="Boolean(activeConfigCode) || version.versionNo === controlPolicyVersions[0]?.versionNo"
                        @click="rollbackControlPolicyVersion(version)"
                      >
                        回滚到此版本
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>告警通知供应商配置</h3>
              <p class="meta">维护 IM / 短信 / 邮件告警的供应商端点、模板、路由、重试和限流策略</p>
            </div>
            <div class="toolbar-actions">
              <button class="button secondary" @click="exportGovernanceSnapshot('ALERT_PROVIDERS')">导出供应商</button>
              <button class="button secondary" @click="startCreateAlertProvider">新建供应商</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ editingAlertProviderCode ? "编辑供应商" : "新增供应商" }}</h3>
                <p class="meta">完整候选经异人声明复核通过后写入运行配置</p>
              </div>
            </div>
            <div class="filter-grid">
              <label>
                供应商编码
                <input v-model.trim="alertProviderForm.providerCode" :disabled="Boolean(editingAlertProviderCode)" maxlength="64" />
              </label>
              <label>
                供应商名称
                <input v-model.trim="alertProviderForm.providerName" maxlength="64" />
              </label>
              <label>
                通知通道
                <select v-model="alertProviderForm.channelCode">
                  <option value="IN_APP">站内</option>
                  <option value="IM">IM</option>
                  <option value="SMS">短信</option>
                  <option value="EMAIL">邮件</option>
                </select>
              </label>
              <label>
                接入端点
                <input v-model.trim="alertProviderForm.endpointAlias" maxlength="64" />
              </label>
              <label>
                模板编码
                <input v-model.trim="alertProviderForm.templateCode" maxlength="64" />
              </label>
              <label>
                路由优先级
                <input v-model.number="alertProviderForm.routePriority" type="number" min="0" />
              </label>
              <label class="wide-field">
                模板正文
                <textarea v-model.trim="alertProviderForm.templateBody" maxlength="1024" rows="3"></textarea>
              </label>
              <label class="wide-field">
                路由规则
                <input v-model.trim="alertProviderForm.routeRule" maxlength="255" />
              </label>
              <label>
                重试策略
                <input v-model.trim="alertProviderForm.retryPolicy" maxlength="128" />
              </label>
              <label>
                限流策略
                <input v-model.trim="alertProviderForm.rateLimitPolicy" maxlength="128" />
              </label>
              <label>
                启用状态
                <select v-model="alertProviderForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
              <label>
                申请人声明
                <input v-model.trim="alertProviderForm.requester" maxlength="64" />
              </label>
              <label class="wide-field">
                申请原因
                <input v-model.trim="alertProviderForm.changeReason" maxlength="255" />
              </label>
            </div>
            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button
                class="button primary"
                :disabled="activeConfigCode === (editingAlertProviderCode || alertProviderForm.providerCode)"
                @click="submitAlertProviderForm"
              >
                {{ editingAlertProviderCode ? "提交修改复核" : "提交新增复核" }}
              </button>
              <button class="button secondary" @click="startCreateAlertProvider">重置表单</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>供应商配置变更复核</h3>
                <p class="meta">同一供应商仅允许一条待办；自声明异人约束不代表可信登录身份或正式审批</p>
              </div>
              <button class="button secondary" :disabled="isAlertProviderReviewLoading" @click="loadAlertProviderReviews">
                {{ isAlertProviderReviewLoading ? "加载中..." : "刷新申请" }}
              </button>
            </div>
            <div class="filter-grid">
              <label>
                启停申请人声明
                <input v-model.trim="alertProviderActionRequester" maxlength="64" />
              </label>
              <label>
                启停申请原因
                <input v-model.trim="alertProviderActionReason" maxlength="255" />
              </label>
              <label>
                复核人声明
                <input v-model.trim="alertProviderReviewer" maxlength="64" />
              </label>
              <label>
                复核意见 / 驳回原因
                <input v-model.trim="alertProviderReviewComment" maxlength="255" />
              </label>
            </div>
            <div v-if="alertProviderReviewError" class="error-banner">
              {{ alertProviderReviews.length ? "供应商复核申请刷新失败，当前展示上次成功快照：" : "供应商复核申请加载失败：" }}{{ alertProviderReviewError }}
            </div>
            <div v-if="isAlertProviderReviewLoading" class="state-box">供应商复核申请加载中...</div>
            <div v-else-if="!alertProviderReviews.length && !alertProviderReviewError" class="state-box">当前暂无供应商配置复核申请</div>
            <div v-else-if="alertProviderReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>操作</th>
                    <th>候选供应商</th>
                    <th>模板与策略</th>
                    <th>候选状态</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in alertProviderReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>{{ review.operationType }}</td>
                    <td>
                      {{ review.providerCode }} / {{ review.providerName }} / {{ review.channelCode }}
                      <div class="muted-text">{{ review.endpointAlias }} / 优先级 {{ review.routePriority }}</div>
                      <div class="muted-text">基线 {{ review.baseFingerprint?.slice(0, 12) || "新增" }}</div>
                    </td>
                    <td class="flow-summary-cell">
                      {{ review.templateCode }} / {{ review.routeRule }}
                      <div class="muted-text">{{ review.retryPolicy }} / {{ review.rateLimitPolicy }}</div>
                    </td>
                    <td><span :class="['badge', review.candidateStatusType]">{{ review.candidateStatus }}</span></td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button class="link-button" :disabled="Boolean(activeConfigCode)" @click="handleAlertProviderReview(review, true)">
                          通过并应用
                        </button>
                        <button class="link-button" :disabled="Boolean(activeConfigCode)" @click="handleAlertProviderReview(review, false)">
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>供应商编码</th>
                  <th>供应商名称</th>
                  <th>通知通道</th>
                  <th>接入端点</th>
                  <th>模板编码</th>
                  <th>模板正文</th>
                  <th>路由规则</th>
                  <th>优先级</th>
                  <th>重试策略</th>
                  <th>限流策略</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="provider in alertProviders" :key="provider.providerCode">
                  <td>{{ provider.providerCode }}</td>
                  <td>{{ provider.providerName }}</td>
                  <td>{{ provider.channelCode }}</td>
                  <td>{{ provider.endpointAlias }}</td>
                  <td>{{ provider.templateCode }}</td>
                  <td class="flow-summary-cell">{{ provider.templateBody }}</td>
                  <td>{{ provider.routeRule }}</td>
                  <td>{{ provider.routePriority }}</td>
                  <td class="flow-summary-cell">{{ provider.retryPolicy }}</td>
                  <td class="flow-summary-cell">{{ provider.rateLimitPolicy }}</td>
                  <td><span :class="['badge', provider.statusType]">{{ provider.status }}</span></td>
                  <td>{{ provider.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === provider.providerCode"
                      @click="startEditAlertProvider(provider)"
                    >
                      编辑
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === provider.providerCode"
                      @click="toggleAlertProvider(provider)"
                    >
                      {{ provider.status === "ENABLED" ? "申请停用" : "申请启用" }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="detail-panel">
          <div class="section-title">
            <div>
              <h3>异常告警值班路由</h3>
              <p class="meta">按异常类型和严重等级配置责任组、值班接收人、通知通道与班次标签，支撑任务中心自动派发异常告警</p>
            </div>
            <div class="toolbar-actions">
              <button class="button secondary" @click="exportGovernanceSnapshot('ISSUE_DUTY_ROSTERS')">导出值班路由</button>
              <button class="button secondary" @click="startCreateRoster">新建值班路由</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>{{ editingRosterCode ? "编辑值班路由" : "新增值班路由" }}</h3>
                <p class="meta">新增和编辑仅提交完整候选，异人声明复核通过后才写入运行态</p>
              </div>
            </div>

            <div class="filter-grid">
              <label>
                路由编码
                <input v-model.trim="rosterForm.rosterCode" :disabled="Boolean(editingRosterCode)" />
              </label>
              <label>
                异常类型
                <input v-model.trim="rosterForm.issueType" placeholder="待回调未收口" />
              </label>
              <label>
                严重等级
                <select v-model="rosterForm.severity">
                  <option value="P1">P1</option>
                  <option value="P2">P2</option>
                  <option value="P3">P3</option>
                </select>
              </label>
              <label>
                责任组
                <input v-model.trim="rosterForm.responsibilityGroup" placeholder="支付后端值班组" />
              </label>
              <label>
                值班接收人
                <input v-model.trim="rosterForm.receiver" placeholder="支付后端值班" />
              </label>
              <label>
                通知通道
                <input v-model.trim="rosterForm.notifyChannels" placeholder="IN_APP,IM,SMS" />
              </label>
              <label>
                升级等级
                <input v-model.trim="rosterForm.escalationLevel" placeholder="L1" />
              </label>
              <label>
                升级接收人
                <input v-model.trim="rosterForm.escalationReceiver" placeholder="支付技术负责人" />
              </label>
              <label>
                升级策略
                <input v-model.trim="rosterForm.escalationPolicy" placeholder="30分钟未确认升级支付技术负责人" />
              </label>
              <label>
                升级超时分钟
                <input v-model.number="rosterForm.escalationTimeoutMinutes" type="number" min="5" max="1440" />
              </label>
              <label>
                班次标签
                <input v-model.trim="rosterForm.scheduleTag" placeholder="交易链路白班" />
              </label>
              <label>
                生效开始小时
                <input v-model.number="rosterForm.effectiveStartHour" type="number" min="0" max="23" />
              </label>
              <label>
                生效结束小时
                <input v-model.number="rosterForm.effectiveEndHour" type="number" min="0" max="23" />
              </label>
              <label>
                适用星期
                <input v-model.trim="rosterForm.weekdayScope" placeholder="1,2,3,4,5" />
              </label>
              <label>
                日期策略
                <select v-model="rosterForm.holidayStrategy">
                  <option value="ALL_DAYS">全部日期</option>
                  <option value="WORKDAY_ONLY">仅工作日</option>
                  <option value="NON_WORKDAY_ONLY">仅非工作日</option>
                </select>
              </label>
              <label>
                启用状态
                <select v-model="rosterForm.enabled">
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </label>
              <label>
                申请人声明
                <input v-model.trim="rosterForm.requester" maxlength="64" />
              </label>
              <label class="wide-field">
                申请原因
                <input v-model.trim="rosterForm.changeReason" maxlength="255" />
              </label>
            </div>

            <div style="display: flex; gap: 12px; margin-top: 16px;">
              <button
                class="button primary"
                :disabled="activeConfigCode === (editingRosterCode || rosterForm.rosterCode)"
                @click="submitRosterForm"
              >
                {{ editingRosterCode ? "提交修改复核" : "提交新增复核" }}
              </button>
              <button class="button secondary" @click="startCreateRoster">重置表单</button>
            </div>
          </div>

          <div class="panel" style="margin-bottom: 16px;">
            <div class="section-title">
              <div>
                <h3>值班路由配置变更复核</h3>
                <p class="meta">新增、编辑和启停共用同一路由编码待办；自声明异人约束不代表可信登录身份或正式审批</p>
              </div>
              <button class="button secondary" :disabled="isIssueDutyRosterReviewLoading" @click="loadIssueDutyRosterReviews">
                {{ isIssueDutyRosterReviewLoading ? "加载中..." : "刷新申请" }}
              </button>
            </div>
            <div class="filter-grid">
              <label>
                启停申请人声明
                <input v-model.trim="issueDutyRosterActionRequester" maxlength="64" placeholder="提交启停申请时必填" />
              </label>
              <label>
                启停申请原因
                <input v-model.trim="issueDutyRosterActionReason" maxlength="255" placeholder="提交启停申请时必填" />
              </label>
              <label>
                复核人声明
                <input v-model.trim="issueDutyRosterReviewer" maxlength="64" placeholder="必填，且须与申请人不同" />
              </label>
              <label>
                复核意见 / 驳回原因
                <input v-model.trim="issueDutyRosterReviewComment" maxlength="255" placeholder="处理待复核申请时必填" />
              </label>
            </div>
            <div v-if="issueDutyRosterReviewError" class="error-banner">
              {{ issueDutyRosterReviews.length ? "值班路由复核申请刷新失败，当前展示上次成功快照：" : "值班路由复核申请加载失败：" }}{{ issueDutyRosterReviewError }}
            </div>
            <div v-if="isIssueDutyRosterReviewLoading" class="state-box">值班路由复核申请加载中...</div>
            <div v-else-if="!issueDutyRosterReviews.length && !issueDutyRosterReviewError" class="state-box">当前暂无值班路由配置复核申请</div>
            <div v-else-if="issueDutyRosterReviews.length" class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>申请号</th>
                    <th>操作</th>
                    <th>候选值班路由</th>
                    <th>班次与升级</th>
                    <th>候选状态</th>
                    <th>申请声明</th>
                    <th>复核状态</th>
                    <th>复核记录</th>
                    <th>时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="review in issueDutyRosterReviews" :key="review.requestNo">
                    <td>{{ review.requestNo }}</td>
                    <td>{{ review.operationType }}</td>
                    <td>
                      {{ review.rosterCode }} / {{ review.issueType }} / {{ review.severity }}
                      <div class="muted-text">{{ review.responsibilityGroup }} / {{ review.receiver }}</div>
                      <div class="muted-text">{{ review.notifyChannels }} / 基线 {{ review.baseFingerprint?.slice(0, 12) || "新增" }}</div>
                    </td>
                    <td>
                      {{ review.scheduleTag }} / {{ review.effectiveStartHour }}:00-{{ review.effectiveEndHour }}:00
                      <div class="muted-text">{{ formatWeekdayScopeLabel(review.weekdayScope) }} / {{ formatHolidayStrategyLabel(review.holidayStrategy) }}</div>
                      <div class="muted-text">{{ review.escalationLevel }} / {{ review.escalationReceiver }} / {{ review.escalationTimeoutMinutes }}分钟</div>
                    </td>
                    <td><span :class="['badge', review.candidateStatusType]">{{ review.candidateStatus }}</span></td>
                    <td>
                      {{ review.requester }}
                      <div class="muted-text">{{ review.requestReason }} / {{ review.identityTrustLevel }}</div>
                    </td>
                    <td><span :class="['badge', review.reviewStatusType]">{{ review.reviewStatus }}</span></td>
                    <td>
                      {{ review.reviewer || "-" }}
                      <div class="muted-text">{{ review.reviewComment || "尚未处理" }}</div>
                    </td>
                    <td>
                      {{ review.createdAt }}
                      <div class="muted-text">{{ review.reviewedAt || "-" }}</div>
                    </td>
                    <td>
                      <template v-if="review.reviewStatus === 'PENDING_REVIEW'">
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleIssueDutyRosterReview(review, true)"
                        >
                          通过并应用
                        </button>
                        <button
                          class="link-button"
                          :disabled="Boolean(activeConfigCode)"
                          @click="handleIssueDutyRosterReview(review, false)"
                        >
                          驳回
                        </button>
                      </template>
                      <span v-else class="muted-text">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>路由编码</th>
                  <th>异常类型</th>
                  <th>严重等级</th>
                  <th>责任组</th>
                  <th>值班接收人</th>
                  <th>通知通道</th>
                  <th>升级等级</th>
                  <th>升级接收人</th>
                  <th>升级策略</th>
                  <th>班次标签</th>
                  <th>生效时间窗</th>
                  <th>适用星期</th>
                  <th>日期策略</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="roster in issueDutyRosters" :key="roster.rosterCode">
                  <td>{{ roster.rosterCode }}</td>
                  <td>{{ roster.issueType }}</td>
                  <td>{{ roster.severity }}</td>
                  <td>{{ roster.responsibilityGroup }}</td>
                  <td>{{ roster.receiver }}</td>
                  <td>{{ roster.notifyChannels }}</td>
                  <td>{{ roster.escalationLevel }}</td>
                  <td>{{ roster.escalationReceiver }}</td>
                  <td>{{ roster.escalationPolicy }} / {{ roster.escalationTimeoutMinutes }}分钟</td>
                  <td>{{ roster.scheduleTag }}</td>
                  <td>{{ roster.effectiveWindow }}</td>
                  <td>{{ formatWeekdayScopeLabel(roster.weekdayScope) || roster.weekdayScope }}</td>
                  <td>{{ roster.applicabilityDesc || formatHolidayStrategyLabel(roster.holidayStrategy) }}</td>
                  <td><span :class="['badge', roster.statusType]">{{ roster.status }}</span></td>
                  <td>{{ roster.updatedAt }}</td>
                  <td>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === roster.rosterCode"
                      @click="startEditRoster(roster)"
                    >
                      编辑
                    </button>
                    <button
                      class="link-button"
                      :disabled="activeConfigCode === roster.rosterCode"
                      @click="toggleIssueDutyRoster(roster)"
                    >
                      {{ roster.status === "ENABLED" ? "申请停用" : "申请启用" }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </fieldset>
    </section>
  </div>
</template>

<style scoped>
.config-content {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
</style>
