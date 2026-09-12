/* Key 绑定(ADMIN):muniu UsageRow.APIKeyID → 控制面用户,计量 ingest 按 key 归账扣费。 */

import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { PageHeader } from "@/components/page-header";
import { FormRow } from "@/components/form-row";
import { errorDetail } from "@/lib/api";
import { bindKey } from "@/lib/keys-api";

export default function KeysPage() {
  const [keyId, setKeyId] = useState("");
  const [userId, setUserId] = useState("");
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const bind = useMutation({
    mutationFn: () => bindKey({ keyId: keyId.trim(), userId: Number(userId) }),
    onSuccess: (out) => {
      setSuccess(`已绑定 ${out.keyId} → 用户 #${out.userId}`);
      setError("");
      setKeyId("");
    },
    onError: (err) => {
      setSuccess("");
      setError(errorDetail(err));
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setSuccess("");
    setError("");
    bind.mutate();
  }

  return (
    <section className="space-y-6">
      <PageHeader title="Key 绑定" />
      <Card>
        <CardHeader>
          <CardTitle>绑定 muniu key 到用户</CardTitle>
          <CardDescription>
            计量 ingest 按 UsageRow.APIKeyID 归账;未绑定的 key 上报将被跳过(skipped_unbound)并计数
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
            <FormRow label="muniu key 标识" htmlFor="keyId">
              <Input
                id="keyId"
                value={keyId}
                onChange={(e) => setKeyId(e.target.value)}
                className="w-64"
                placeholder="mk-…"
                required
              />
            </FormRow>
            <FormRow label="用户 ID" htmlFor="userId">
              <Input
                id="userId"
                type="number"
                min="1"
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                className="w-32"
                required
              />
            </FormRow>
            <Button type="submit" disabled={bind.isPending || !keyId || !userId}>
              {bind.isPending ? "绑定中…" : "绑定"}
            </Button>
            {error && <p className="text-sm text-destructive">{error}</p>}
            {success && <span className="text-sm text-muted-foreground">{success}</span>}
          </form>
        </CardContent>
      </Card>
    </section>
  );
}
