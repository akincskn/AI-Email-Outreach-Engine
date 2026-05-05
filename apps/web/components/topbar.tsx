import { Bell } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { api } from "@/lib/api";
import type { AnalyticsSummary } from "@/lib/types";
import { signOut } from "@/auth";

async function getUnhandledCount() {
  try {
    const summary = await api.get<AnalyticsSummary>("/api/v1/analytics/summary");
    return summary.unhandledReplies;
  } catch {
    return 0;
  }
}

export async function Topbar() {
  const unhandled = await getUnhandledCount();

  return (
    <header className="h-12 border-b px-4 flex items-center justify-between bg-background">
      <div />
      <div className="flex items-center gap-2">
        <a href="/replies" className="relative inline-flex items-center justify-center h-8 w-8 rounded-md hover:bg-muted transition-colors">
          <Bell className="h-4 w-4" />
          {unhandled > 0 && (
            <Badge
              variant="destructive"
              className="absolute -top-1 -right-1 h-4 w-4 p-0 flex items-center justify-center text-[10px]"
            >
              {unhandled > 9 ? "9+" : unhandled}
            </Badge>
          )}
        </a>
        <form
          action={async () => {
            "use server";
            await signOut({ redirectTo: "/login" });
          }}
        >
          <Button variant="ghost" size="sm" type="submit" className="text-xs">
            Sign out
          </Button>
        </form>
      </div>
    </header>
  );
}
