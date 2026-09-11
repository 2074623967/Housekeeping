const PAYMENT_AMOUNT_PATTERN = /^(\d*)(?:\.(\d{1,2}))?$/;
const PAYMENT_AMOUNT_MAX_INTEGER_DIGITS = 16;

function normalizePaymentAmountInputValue(value, allowZero) {
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
  return allowZero || /[1-9]/.test(normalized) ? normalized : null;
}

export function normalizePaymentAmountInput(value) {
  return normalizePaymentAmountInputValue(value, false);
}

export function normalizeNonNegativePaymentAmountInput(value) {
  return normalizePaymentAmountInputValue(value, true);
}

function toMinorUnitDigits(value) {
  const rawValue = String(value ?? "").trim().replace(/^¥\s*/, "").replace(/,/g, "");
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(rawValue);
  if (!match) {
    return "0";
  }
  const fractionPart = (match[2] || "").padEnd(2, "0");
  return `${match[1]}${fractionPart}`.replace(/^0+(?=\d)/, "");
}

function addUnsignedIntegerStrings(left, right) {
  let carry = 0;
  let result = "";
  for (let leftIndex = left.length - 1, rightIndex = right.length - 1;
    leftIndex >= 0 || rightIndex >= 0 || carry;
    leftIndex -= 1, rightIndex -= 1) {
    const sum = Number(left[leftIndex] || 0) + Number(right[rightIndex] || 0) + carry;
    result = `${sum % 10}${result}`;
    carry = Math.floor(sum / 10);
  }
  return result.replace(/^0+(?=\d)/, "");
}

export function sumPaymentAmounts(values) {
  const minorUnits = values.reduce(
    (sum, value) => addUnsignedIntegerStrings(sum, toMinorUnitDigits(value)),
    "0"
  ).padStart(3, "0");
  return `${minorUnits.slice(0, -2)}.${minorUnits.slice(-2)}`;
}
