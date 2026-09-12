const BUSINESS_DATE_FORMATTER = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit"
});

export function previousShanghaiBusinessDate(now = new Date()) {
  if (!(now instanceof Date) || Number.isNaN(now.getTime())) {
    throw new TypeError("now must be a valid Date");
  }

  const parts = Object.fromEntries(
    BUSINESS_DATE_FORMATTER.formatToParts(now).map(({ type, value }) => [type, value])
  );
  const shanghaiDate = new Date(Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day)
  ));
  shanghaiDate.setUTCDate(shanghaiDate.getUTCDate() - 1);

  const year = shanghaiDate.getUTCFullYear();
  const month = String(shanghaiDate.getUTCMonth() + 1).padStart(2, "0");
  const day = String(shanghaiDate.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
