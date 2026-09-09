export function buildPaymentAccessTokenStorageKey(sourceAppId) {
  return `hsp.accessToken.${sourceAppId}`;
}

function resolveSessionStorage() {
  try {
    return globalThis.sessionStorage || null;
  } catch (error) {
    return null;
  }
}

export function persistPaymentAccessToken(sourceAppId, accessToken, storage = resolveSessionStorage()) {
  if (!storage || !sourceAppId || !accessToken) {
    return false;
  }
  try {
    storage.setItem(buildPaymentAccessTokenStorageKey(sourceAppId), accessToken);
    return true;
  } catch (error) {
    return false;
  }
}

export function readPaymentAccessToken(sourceAppId, storage = resolveSessionStorage()) {
  if (!storage || !sourceAppId) {
    return "";
  }
  try {
    return storage.getItem(buildPaymentAccessTokenStorageKey(sourceAppId)) || "";
  } catch (error) {
    return "";
  }
}

export function withoutAccessToken(query = {}) {
  const sanitizedQuery = { ...query };
  delete sanitizedQuery.accessToken;
  return sanitizedQuery;
}
