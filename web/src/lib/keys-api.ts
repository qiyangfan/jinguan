/* 管理域 API:key 绑定(计量 ingest 归账的身份映射)。 */

import { api } from "./api";
import type { KeyBindingRequest } from "./api-types.gen";

export function bindKey(req: KeyBindingRequest): Promise<{ keyId: string; userId: number }> {
  return api.post("/admin/key-bindings", req);
}
