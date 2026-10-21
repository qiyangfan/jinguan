/* 统一金额换算：1 元 = 1,000,000,000 credit；格式化使用 BigInt 避免浮点误差。 */

const CREDITS_PER_YUAN = 1_000_000_000n;

export function credits2yuan(credits: number | string): string {
  const n = BigInt(credits);
  const sign = n < 0n ? "-" : "";
  const abs = n < 0n ? -n : n;
  const yuan = abs / CREDITS_PER_YUAN;
  const rem = abs % CREDITS_PER_YUAN;
  const frac = rem.toString().padStart(9, "0").replace(/0+$/, "");
  return `${sign}${yuan.toLocaleString("en-US")}.${frac || "0"}`;
}

export function credits2yuanInput(credits: number | string): string {
  const n = BigInt(credits);
  const sign = n < 0n ? "-" : "";
  const abs = n < 0n ? -n : n;
  return `${sign}${(abs / CREDITS_PER_YUAN).toString()}.${(abs % CREDITS_PER_YUAN).toString().padStart(9, "0").replace(/0+$/, "")}`;
}

export function yuan2credits(yuan: string): number {
  return Math.round(parseFloat(yuan) * 1_000_000_000);
}

export function yuanPerMillion2credits(yuanPerMillion: string): number {
  return Math.round(parseFloat(yuanPerMillion) * 1000);
}

export function credits2yuanPerMillion(credits: number): string {
  return String(Number((credits / 1000).toFixed(3)));
}
