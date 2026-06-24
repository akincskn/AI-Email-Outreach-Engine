export interface Company {
  id: string;
  domain: string;
  name: string;
  websiteUrl: string | null;
  source: string;
  discoveredAt: string;
  countryCode: string | null;
  city: string | null;
  analysis: Record<string, unknown> | null;
  analysisAt: string | null;
  status: string;
  statusReason: string | null;
  createdAt: string;
}

export interface EmailAccount {
  id: string;
  companyId: string;
  email: string;
  prefixType: string | null;
  extractedAt: string;
}

export interface EmailDraft {
  id: string;
  companyId: string;
  companyName: string;
  companyDomain: string;
  emailAccountId: string;
  toEmail: string;
  subject: string;
  bodyHtml: string;
  bodyText: string;
  language: string;
  modelUsed: string | null;
  personalizationSignals: string[];
  warnings: string[];
  status: string;
  editedSubject: string | null;
  editedBodyHtml: string | null;
  createdAt: string;
  approvedAt: string | null;
}

export interface EmailSend {
  id: string;
  draftId: string;
  companyId: string;
  companyName: string;
  toEmail: string;
  subject: string;
  status: string;
  retryCount: number;
  queuedAt: string;
  sentAt: string | null;
  failedAt: string | null;
  hasOpen: boolean;
  hasReply: boolean;
}

export interface EmailReply {
  id: string;
  sendId: string | null;
  companyName: string | null;
  fromEmail: string;
  subject: string | null;
  bodyText: string | null;
  classification: string | null;
  handled: boolean;
  receivedAt: string;
}

export interface SuppressionEntry {
  id: string;
  email: string;
  reason: string;
  notes: string | null;
  suppressedAt: string;
  expiresAt: string | null;
}

export interface DiscoveryFilter {
  id: string;
  name: string;
  industry: string | null;
  countryCode: string | null;
  city: string | null;
  cities: string[] | null;
  keywords: string[];
  targetProduct: string | null;
  dailyQuota: number;
  active: boolean;
  createdAt: string;
}

export interface AnalyticsSummary {
  sentToday: number;
  openedToday: number;
  repliedToday: number;
  bouncedToday: number;
  pendingDrafts: number;
  unhandledReplies: number;
  dailyCap: number;
  volumeUsedToday: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
