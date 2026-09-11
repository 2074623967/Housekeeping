const PAYMENT_AMOUNT_PATTERN = /^(\d*)(?:\.(\d{1,2}))?$/;
const PAYMENT_AMOUNT_MAX_INTEGER_DIGITS = 16;

export function normalizePaymentAmountInput(value) {
  const rawValue = typeof value === "string" ? value.trim() : String(value ?? "").trim();
  const match = PAYMENT_AMOUNT_PATTERN.exec(rawValue);
  if (!match || (!match[1] && !match[2])) {
    return null;
  }

  const integerPart = (match[1] || "0").replace(/^0+(?=\d)/, "");
  const fractionPart = match[2];
  if (integerPart.length > PAYMENT_AMOUNT_MAX_INTEGER_DIGITS) {
    return null;
  }

  const normalized = fractionPart === undefined
    ? integerPart
    : `${integerPart}.${fractionPart}`;
  return /[1-9]/.test(normalized) ? normalized : null;
}
