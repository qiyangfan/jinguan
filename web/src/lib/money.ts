/* 金额展示纪律(对齐 kongming money.ts):控制面金额存储单位=μ(1 元=1_000_000 μ),
   与 muniu CostMicro 同口径;展示层一律经此转元,禁止把 μ 数值原样示人。
   整数运算拼小数,不走浮点除法,避免 0.1 类二进制误差进展示。 */

export function micros2yuan(micros: number): string {
  const sign = micros < 0 ? "-" : "";
  const abs = Math.abs(micros);
  const yuan = Math.trunc(abs / 1_000_000);
  const rem = abs % 1_000_000;
  return `${sign}${yuan.toLocaleString()}.${String(rem).padStart(6, "0").replace(/0+$/, "") || "0"}`;
}

/** 录入回填用:μ→元的裸数字串(如 500→"0.0005"),不带千分位。 */
export function micros2yuanInput(micros: number): string {
  return (micros / 1_000_000).toString();
}

/** 录入提交用:元(十进制)→ μ 整数。 */
export function yuan2micros(yuan: string): number {
  return Math.round(parseFloat(yuan) * 1_000_000);
}
