/* 管理 muniu key 与控制面用户的绑定。 */

import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { PageHeader } from "@/components/page-header";
import { FormRow } from "@/components/form-row";
import { FormGrid } from "@/components/form-grid";
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
      setSuccess(`密钥 ${out.keyId} 已绑定到用户(ID ${out.userId})`);
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
      <PageHeader title="密钥绑定" />
      <Card>
        <CardHeader>
          <CardTitle>将密钥绑定到用户</CardTitle>
          <CardDescription>
            用量按密钥归属自动记账;未绑定用户的密钥产生的用量不会计入账单
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit}>
            <FormGrid>
              <FormRow
                label="密钥标识（Key ID）"
                htmlFor="keyId"
                help="从木牛密钥列表、签发结果或 CLI keys list 复制的记录 ID(与计量 APIKeyID 同值),按原值粘贴。不要填写 mk- 开头的一次性明文凭据或展示前缀。"
                className="md:w-64"
              >
                <Input
                  id="keyId"
                  value={keyId}
                  onChange={(e) => setKeyId(e.target.value)}
                  className="w-full"
                  placeholder="粘贴木牛密钥记录 ID,如 k-1"
                  required
                />
              </FormRow>
              <FormRow label="用户 ID" htmlFor="userId" className="md:w-32">
                <Input
                  id="userId"
                  type="number"
                  inputMode="numeric"
                  min="1"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  className="w-full"
                  required
                />
              </FormRow>
              <Button
                type="submit"
                className="h-11 w-full md:h-9 md:w-auto"
                disabled={bind.isPending || !keyId.trim() || !userId}
              >
                {bind.isPending ? "绑定中…" : "绑定"}
              </Button>
              {error && <p className="text-sm text-destructive">{error}</p>}
              {success && <span className="text-sm text-muted-foreground">{success}</span>}
            </FormGrid>
          </form>
        </CardContent>
      </Card>
    </section>
  );
}
