/* 金额展示纪律(对齐 kongming money.ts):控制面金额存储单位=credit(1 元=1_000_000_000 credit,即本币 1e-9),
   与 muniu creditPerUnit=1e9 / cost_credits 同口径;出站一律为 string(JS Number 仅安全到 2^53)。
   展示层一律经此转元,禁止把 credit 数值原样示人;BigInt 整数运算拼小数,不走浮点。 */

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

/** 录入回填用:credit→元的裸数字串(如 500→"0.0000005"),不带千分位。 */
export function credits2yuanInput(credits: number | string): string {
  const n = BigInt(credits);
  const sign = n < 0n ? "-" : "";
  const abs = n < 0n ? -n : n;
  return `${sign}${(abs / CREDITS_PER_YUAN).toString()}.${(abs % CREDITS_PER_YUAN).toString().padStart(9, "0").replace(/0+$/, "")}`;
}

/** 录入提交用:元(十进制)→ credit 整数。入站数值恒在安全范围(用户表单 ≤ 数百万 → ≤ 1e15 < 2^53)。 */
export function yuan2credits(yuan: string): number {
  return Math.round(parseFloat(yuan) * 1_000_000_000);
}

/** 价目口径换算:元/百万 tokens → credit/token(1 元/百万 = 1000 credit/token)。 */
export function yuanPerMillion2credits(yuanPerMillion: string): number {
  return Math.round(parseFloat(yuanPerMillion) * 1000);
}

/** 价目口径换算(回填):credit/token → 元/百万 tokens(3 位小数精度,与录入粒度一致)。 */
export function credits2yuanPerMillion(credits: number): string {
  return String(Number((credits / 1000).toFixed(3)));
}
