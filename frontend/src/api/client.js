async function request(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  return response.json();
}

export const dashboardApi = {
  getSummary: () => request("/api/dashboard/summary")
};

export const orderApi = {
  getList: () => request("/api/orders")
};

export const paymentApi = {
  getList: () => request("/api/payments")
};

export const refundApi = {
  getList: () => request("/api/refunds")
};

export const settlementApi = {
  getWorkerList: () => request("/api/settlements/workers")
};
