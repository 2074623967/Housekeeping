export function buildPaymentAccessTokenStorageKey(sourceAppId) {
  return `hsp.accessToken.${sourceAppId}`;
}

export function resolveConfiguredPaymentAccessToken(env, envKey, developmentFallback = "") {
  const configuredToken = typeof env?.[envKey] === "string" ? env[envKey].trim() : "";
  if (configuredToken) {
    return configuredToken;
  }
  return env?.DEV === true ? developmentFallback : "";
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
