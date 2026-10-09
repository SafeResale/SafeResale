export type RiskBand = "low" | "medium" | "high";

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  page_size: number;
  statuses?: string[];
}

export interface ListingItem {
  listing: {
    _id: string;
    title?: string;
    description?: string;
    category?: string;
    price?: number;
    currency?: string;
    status?: string;
    condition?: any;
    seller_id?: string;
    created_at?: number;
    updated_at?: number;
    [key: string]: any;
  };
  risk?: { adjusted_score?: number; badge?: string; [key: string]: any } | null;
  risk_band?: RiskBand | null;
  decision?: { status?: string; reason?: string; [key: string]: any } | null;
  image_count?: number;
  seller?: { id: string; name?: string | null; email?: string | null; status?: string | null } | null;
}

export interface DashboardData {
  kpis: {
    total_listings: number;
    pending_review: number;
    high_risk: number;
    approval_rate: number;
    avg_risk: number;
    model_confidence: number;
    new_today: number;
    new_7d: number;
    users: { total: number; sellers: number; buyers: number; admins: number; inspectors: number; suspended: number };
    escrow_held: number;
    escrow_in_review: number;
    escrow_counts: Record<string, number>;
    reports_pending: number;
    messages_new: number;
  };
  status_distribution: Record<string, number>;
  risk_distribution: Record<string, number>;
  risk_samples: number;
  trend: { date: number; count: number }[];
  recent_flagged: ListingItem[];
  recent_activity: AuditEntry[];
  generated_at: number;
}

export interface AuditEntry {
  _id: string;
  actor_id?: string;
  actor_role?: string;
  action: string;
  target_type?: string;
  target_id?: string;
  detail?: any;
  ip?: string;
  created_at?: number;
  [key: string]: any;
}

export interface UserRow {
  _id: string;
  name?: string;
  email: string;
  phone?: string | null;
  role: string;
  roles?: string[];
  status: string;
  verified?: boolean;
  created_at?: number;
  updated_at?: number;
  listing_count?: number;
  [key: string]: any;
}

export interface UserDetail {
  user: UserRow;
  stats: {
    listings_by_status: Record<string, number>;
    listing_count: number;
    escrows_as_buyer: number;
    escrows_as_seller: number;
    reports_against: number;
    active_sessions: number;
  };
  listings: any[];
  behavior: { top_signals: any[]; features: Record<string, any> };
}

export interface Category {
  _id: string;
  name: string;
  slug: string;
  description?: string;
  icon?: string;
  fields?: string[];
  active: boolean;
  sort?: number;
  created_at?: number;
  updated_at?: number;
  listing_count?: number;
}

export interface Report {
  _id: string;
  target_type: "listing" | "user";
  target_id: string;
  reporter_id?: string;
  reason: string;
  description?: string;
  status: "pending" | "resolved" | "dismissed";
  resolution_note?: string;
  resolved_at?: number;
  created_at?: number;
  target?: { id: string; title?: string; name?: string; email?: string; status?: string; role?: string };
  reporter?: { id: string; name?: string | null; email?: string | null };
}

export interface ContactMessage {
  _id: string;
  name?: string;
  email?: string;
  subject?: string;
  message?: string;
  status: "new" | "read" | "resolved" | "archived";
  created_at?: number;
}

export interface NotificationItem {
  _id: string;
  title: string;
  body?: string;
  audience: string;
  status: string;
  sent_by?: string;
  sent_at?: number;
  recipient_count?: number;
  delivery?: string;
  created_at?: number;
  author?: { id: string; name?: string | null; email?: string | null };
}

export interface ContentItem {
  _id: string;
  status: "published" | "draft";
  created_at?: number;
  updated_at?: number;
  [key: string]: any;
}

export interface EscrowRow {
  _id: string;
  listing_id?: string;
  buyer_id?: string;
  seller_id?: string;
  amount?: number;
  currency?: string;
  status: "pending" | "held" | "in_review" | "released" | "refunded";
  created_at?: number;
  updated_at?: number;
  [key: string]: any;
}

export interface SettingMeta {
  key: string;
  type: string;
  description?: string;
  value: any;
  group?: string;
  overridden?: boolean;
}

export interface SettingsData {
  groups: Record<string, Record<string, SettingMeta>>;
  group_names: string[];
}

export interface SystemStatus {
  status: string;
  db: { status: string; latency_ms?: number };
  storage: { status: string; message?: string };
  vision: { provider: string; configured: boolean; message?: string };
  [key: string]: any;
}

export interface ModelInfo {
  _id: string;
  name?: string;
  version?: string;
  status?: string;
  accuracy?: number;
  updated_at?: number;
  [key: string]: any;
}