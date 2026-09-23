#!/usr/bin/env node

import { pathToFileURL } from "node:url";

const COMMANDS = new Set([
  "prepay",
  "cashier",
  "submit",
  "detail",
  "query",
  "close",
  "refund-apply",
  "refund-detail",
  "refund-query",
  "refund-retry"
]);

export function parseArguments(argv) {
  const [command, ...tokens] = argv;
  if (!COMMANDS.has(command)) {
    throw new Error("命令仅支持 prepay、cashier、submit、detail、query、close、refund-apply、refund-detail、refund-query 或 refund-retry");
  }
  const options = {};
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (!token.startsWith("--")) {
      throw new Error(`无法识别参数：${token}`);
    }
    const separatorIndex = token.indexOf("=");
    const key = separatorIndex > 2 ? token.slice(2, separatorIndex) : token.slice(2);
    const value = separatorIndex > 2 ? token.slice(separatorIndex + 1) : tokens[++index];
    if (!key || value == null || value.startsWith("--")) {
      throw new Error(`参数 --${key || "unknown"} 缺少值`);
    }
    if (Object.prototype.hasOwnProperty.call(options, key)) {
      throw new Error(`参数 --${key} 不能重复`);
    }
    options[key] = value;
  }
  return { command, options };
}

function requireOption(options, key) {
  const value = options[key] && options[key].trim();
  if (!value) {
    throw new Error(`缺少必填参数 --${key}`);
  }
  return value;
}

export function buildRequest(command, options, now = Date.now()) {
  if (command === "prepay") {
    return {
      method: "POST",
      path: "/api/payments/prepay",
      data: {
        orderNo: requireOption(options, "order-no"),
        payScene: requireOption(options, "pay-scene"),
        customerName: requireOption(options, "customer-name"),
        amount: requireOption(options, "amount"),
        cashierTitle: requireOption(options, "cashier-title"),
        ...(options.remark && options.remark.trim() ? { remark: options.remark.trim() } : {})
      }
    };
  }
  if (command === "cashier") {
    return {
      method: "GET",
      path: `/api/payments/cashier/${encodeURIComponent(requireOption(options, "prepay-order-no"))}`
    };
  }
  if (command === "submit") {
    const prepayOrderNo = requireOption(options, "prepay-order-no");
    return {
      method: "POST",
      path: "/api/payments/submit",
      data: {
        prepayOrderNo,
        paymentMethod: options["payment-method"] || "支付宝",
        channelCode: options["channel-code"] || "ALIPAY_H5",
        sourceAppId: options["source-app-id"] || "housekeeping-api",
        merchantNo: options["merchant-no"] || "MCH_ALI_20001",
        accessToken: requireOption(options, "access-token"),
        terminal: "API",
        clientDeviceId: options["client-device-id"] || "payment-api-client",
        idempotencyKey: options["idempotency-key"] || `API-${prepayOrderNo}-${now}`
      }
    };
  }
  if (command === "refund-apply") {
    const paymentOrderId = requireOption(options, "payment-order-id");
    return {
      method: "POST",
      path: "/api/refunds/apply",
      data: {
        paymentOrderId,
        idempotencyKey: options["idempotency-key"] || `API-REFUND-${paymentOrderId}-${now}`,
        refundAmount: requireOption(options, "refund-amount"),
        refundMethod: options["refund-method"] || "原路退款",
        refundReason: options["refund-reason"] || "服务调用端退款申请",
        operator: requireOption(options, "operator")
      }
    };
  }
  if (command === "refund-detail") {
    return {
      method: "GET",
      path: `/api/refunds/${encodeURIComponent(requireOption(options, "refund-order-id"))}`
    };
  }
  if (command === "refund-query") {
    return {
      method: "POST",
      path: "/api/refunds/query",
      data: {
        refundOrderId: requireOption(options, "refund-order-id"),
        operator: requireOption(options, "operator")
      }
    };
  }
  if (command === "refund-retry") {
    return {
      method: "POST",
      path: "/api/refunds/retry",
      data: {
        refundOrderId: requireOption(options, "refund-order-id"),
        operator: requireOption(options, "operator"),
        ...(options.remark && options.remark.trim() ? { remark: options.remark.trim() } : {})
      }
    };
  }
  const paymentOrderId = requireOption(options, "payment-order-id");
  if (command === "detail") {
    return {
      method: "GET",
      path: `/api/payments/${encodeURIComponent(paymentOrderId)}`
    };
  }
  return {
    method: "POST",
    path: `/api/payments/${command}`,
    data: { paymentOrderId }
  };
}

export function resolveBaseUrl(options, environment = process.env) {
  const value = options["base-url"] || environment.PAYMENT_API_BASE_URL || "http://127.0.0.1:18081";
  return value.replace(/\/$/, "");
}

async function readResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("支付服务返回了无法解析的响应");
    parseError.code = "PAYMENT-API-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

export async function executeRequest(baseUrl, request, fetchImpl = fetch) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 10000);
  try {
    let response;
    try {
      response = await fetchImpl(`${baseUrl}${request.path}`, {
        method: request.method,
        headers: {
          "Content-Type": "application/json",
          "X-Terminal": "API"
        },
        body: request.data ? JSON.stringify(request.data) : undefined,
        signal: controller.signal
      });
    } catch (error) {
      const transportError = new Error(error?.name === "AbortError" ? "支付服务请求超时" : "无法连接支付服务");
      transportError.code = error?.name === "AbortError"
        ? "PAYMENT-API-CLIENT-TIMEOUT"
        : "PAYMENT-API-CLIENT-NETWORK";
      transportError.cause = error;
      throw transportError;
    }
    const payload = await readResponsePayload(response);
    if (!response.ok || !payload || payload.code !== "0") {
      const error = new Error(payload && payload.message ? payload.message : `HTTP ${response.status}`);
      error.payload = payload;
      throw error;
    }
    return payload;
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function main(argv = process.argv.slice(2), environment = process.env) {
  const { command, options } = parseArguments(argv);
  const request = buildRequest(command, options);
  const payload = await executeRequest(resolveBaseUrl(options, environment), request);
  process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch((error) => {
    const output = error.payload || {
      code: error.code || "PAYMENT-API-CLIENT",
      message: error.message,
      ...(error.status ? { status: error.status } : {})
    };
    process.stderr.write(`${JSON.stringify(output, null, 2)}\n`);
    process.exitCode = 1;
  });
}
