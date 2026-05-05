import { api } from "@/lib/api";
import type { EmailDraft } from "@/lib/types";
import { notFound } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { DraftActions } from "./draft-actions";

interface Props { params: { id: string } }

export default async function DraftDetailPage({ params }: Props) {
  let draft: EmailDraft;
  try {
    draft = await api.get<EmailDraft>(`/api/v1/drafts/${params.id}`);
  } catch {
    notFound();
  }

  return (
    <div className="space-y-4 max-w-5xl">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl font-semibold">{draft.companyName}</h1>
          <p className="text-sm text-muted-foreground">{draft.toEmail} · {draft.companyDomain}</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={draft.status === "PENDING" ? "secondary" : "outline"}>{draft.status}</Badge>
          <Badge variant="outline">{draft.language.toUpperCase()}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Left: company signals */}
        <div className="space-y-4">
          <div>
            <h2 className="text-sm font-medium mb-2">Personalization Signals</h2>
            {draft.personalizationSignals.length > 0 ? (
              <ul className="space-y-1">
                {draft.personalizationSignals.map((s, i) => (
                  <li key={i} className="text-sm text-muted-foreground flex gap-2">
                    <span className="text-primary">·</span>{s}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">No signals</p>
            )}
          </div>

          {draft.warnings.length > 0 && (
            <div>
              <h2 className="text-sm font-medium mb-2 text-destructive">Warnings</h2>
              <ul className="space-y-1">
                {draft.warnings.map((w, i) => (
                  <li key={i} className="text-xs text-destructive">{w}</li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Right: email content */}
        <div className="space-y-4">
          <div>
            <h2 className="text-sm font-medium mb-1">Subject</h2>
            <p className="text-sm border rounded-md p-2 bg-muted/30">{draft.editedSubject ?? draft.subject}</p>
          </div>
          <div>
            <h2 className="text-sm font-medium mb-1">Body Preview</h2>
            <div
              className="text-xs border rounded-md p-3 bg-muted/30 max-h-64 overflow-y-auto"
              dangerouslySetInnerHTML={{ __html: draft.editedBodyHtml ?? draft.bodyHtml }}
            />
          </div>
        </div>
      </div>

      <Separator />

      {(draft.status === "PENDING" || draft.status === "EDITED") && (
        <DraftActions
          draftId={draft.id}
          defaultSubject={draft.editedSubject ?? draft.subject}
          defaultBodyHtml={draft.editedBodyHtml ?? draft.bodyHtml}
        />
      )}
    </div>
  );
}
