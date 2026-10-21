/* 管理域 API:key 绑定(计量 ingest 归账的身份映射)与终态吊销。 */

import { client, unwrap } from "./api";
import type { components } from "./api-types.gen";

type Schemas = components["schemas"];
export type KeyBindingRequest = Schemas["BindRequest"];
export type RevokeRequest = Schemas["RevokeRequest"];

export function bindKey(req: KeyBindingRequest): Promise<{ keyId: string; userId: string }> {
  return unwrap(client.POST("/admin/key-bindings", { body: req }));
}

/** 终态吊销；已吊销 key 重绑将被拒绝。 */
export function revokeKey(
  keyId: string,
  reason: string,
): Promise<{ keyId: string; replayed: boolean }> {
  return unwrap(
    client.POST("/admin/keys/{keyId}/revoke", {
      params: { path: { keyId } },
      body: { reason },
    }),
  );
}
