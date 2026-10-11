const allowedHspVariables = new Set([
  "HSP_MAVEN_BIN", "HSP_MAVEN_REPO", "HSP_JAVA_HOME"
]);

export function assertLocalGatewaySmokeEnvironment(environment, { allowQueryRaceMode = false } = {}) {
  for (const [name, value] of Object.entries(environment)) {
    if (!value) continue;
    if (name === "HSP_QUERY_RACE_MODE" && allowQueryRaceMode && value === "conflicting") continue;
    if ((name.startsWith("HSP_") && !allowedHspVariables.has(name))
        || /^(SPRING_|GATEWAY_|PAYMENT_|RISK_CONTROL_|OPS_CONFIG_|REFUND_CENTER_)/.test(name)
        || ["DATABASE_URL", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "MAVEN_OPTS",
          "NODE_OPTIONS", "NODE_USE_ENV_PROXY", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY",
          "http_proxy", "https_proxy", "all_proxy"].includes(name)) {
      throw new Error(`Local gateway smoke refuses environment override: ${name}`);
    }
  }
}
