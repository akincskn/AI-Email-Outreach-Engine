"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import {
  LayoutDashboard,
  Building2,
  FileText,
  Send,
  MessageSquare,
  ShieldOff,
  Settings,
  Workflow,
} from "lucide-react";

const navItems = [
  { href: "/",            label: "Dashboard",   icon: LayoutDashboard },
  { href: "/pipeline",    label: "Pipeline",    icon: Workflow },
  { href: "/companies",   label: "Companies",   icon: Building2 },
  { href: "/drafts",      label: "Drafts",      icon: FileText },
  { href: "/sent",        label: "Sent",        icon: Send },
  { href: "/replies",     label: "Replies",     icon: MessageSquare },
  { href: "/suppression", label: "Suppression", icon: ShieldOff },
  { href: "/settings",    label: "Settings",    icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-56 border-r bg-background flex flex-col h-screen sticky top-0">
      <div className="px-4 py-5 border-b">
        <span className="font-semibold text-sm">AI Outreach Engine</span>
      </div>
      <nav className="flex-1 p-2 space-y-0.5">
        {navItems.map(({ href, label, icon: Icon }) => {
          const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex items-center gap-2.5 px-3 py-2 rounded-md text-sm transition-colors",
                active
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground"
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
