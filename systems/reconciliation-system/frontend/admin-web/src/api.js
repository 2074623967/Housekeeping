const request = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const body = await response.json();
  if (!response.ok || body.code !== "0") throw new Error(body.message || "请求失败");
  return body.data;
};

export const getOverview = () => request("/api/reconciliation/overview");
export const getBatches = () => request("/api/reconciliation/batches");
export const getDifferences = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => value && params.set(key, value));
  return request(`/api/reconciliation/differences?${params.toString()}`);
};
export const runBatch = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/run`, { method: "POST" });
export const resolveDifference = (differenceNo, payload) =>
  request(`/api/reconciliation/differences/${encodeURIComponent(differenceNo)}/resolve`, {
    method: "POST",
    body: JSON.stringify(payload)
  });

