#!/usr/bin/env node

import { createServer } from "node:http";

let armedPayment = null;
const queries = [];

const server = createServer(async (incoming, outgoing) => {
  const send = (status, data) => {
    outgoing.writeHead(status, { "Content-Type": "application/json" });
    outgoing.end(JSON.stringify(data));
  };
  if (incoming.method === "GET" && incoming.url === "/health") {
    send(200, { code: "0", data: "local-payment-query-upstream" });
    return;
  }
  if (incoming.method === "GET" && incoming.url === "/calls") {
    send(200, { code: "0", data: { total: queries.length, items: queries } });
    return;
  }
  if (incoming.method !== "POST" || !["/arm", "/query"].includes(incoming.url)) {
    send(404, { code: "NOT_FOUND" });
    return;
  }
  try {
    const chunks = [];
    for await (const chunk of incoming) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (incoming.url === "/arm") {
      if (armedPayment || typeof body.paymentOrderId !== "string" || !body.paymentOrderId
          || typeof body.orderNo !== "string" || !body.orderNo
          || typeof body.channelTransactionNo !== "string" || !body.channelTransactionNo) {
        send(409, { code: "INVALID_FIXTURE" });
        return;
      }
      armedPayment = body;
      send(200, { code: "0", data: { armed: true } });
      return;
    }
    if (!armedPayment || body.paymentOrderId !== armedPayment.paymentOrderId
        || body.orderNo !== armedPayment.orderNo || body.channelCode !== "wx_h5"
        || body.channelTransactionNo !== armedPayment.channelTransactionNo) {
      send(409, { code: "UNBOUND_QUERY" });
      return;
    }
    queries.push(body);
    send(200, { code: "0", data: {
      paymentOrderId: armedPayment.paymentOrderId,
      orderNo: armedPayment.orderNo,
      tradeStatus: queries.length === 1 ? "PAID" : "SUCCESS",
      channelTransactionNo: armedPayment.channelTransactionNo,
      querySource: "LOCAL_QUERY_CHAIN_STUB"
    } });
  } catch {
    send(400, { code: "INVALID_JSON" });
  }
});

server.listen(18190, "127.0.0.1");
