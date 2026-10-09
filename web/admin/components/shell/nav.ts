import {
  Bell,
  Cpu,
  FileText,
  Flag,
  FolderTree,
  HelpCircle,
  LayoutDashboard,
  Lightbulb,
  Mail,
  Package,
  ScrollText,
  Settings,
  ShieldAlert,
  UserCircle,
  Users,
  Wallet,
} from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: any;
  segment: string;
}

export interface NavGroup {
  title: string;
  items: NavItem[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    title: "Overview",
    items: [
      { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, segment: "dashboard" },
      { href: "/queue", label: "Queue", icon: ShieldAlert, segment: "queue" },
      { href: "/audit", label: "Audit trail", icon: ScrollText, segment: "audit" },
    ],
  },
  {
    title: "Marketplace",
    items: [
      { href: "/listings", label: "Listings", icon: Package, segment: "listings" },
      { href: "/categories", label: "Categories", icon: FolderTree, segment: "categories" },
      { href: "/escrow", label: "Escrow", icon: Wallet, segment: "escrow" },
    ],
  },
  {
    title: "People & Support",
    items: [
      { href: "/users", label: "Users", icon: Users, segment: "users" },
      { href: "/reports", label: "Reports", icon: Flag, segment: "reports" },
      { href: "/messages", label: "Messages", icon: Mail, segment: "messages" },
    ],
  },
  {
    title: "Content",
    items: [
      { href: "/content/blogs", label: "Blogs", icon: FileText, segment: "blogs" },
      { href: "/content/faqs", label: "FAQs", icon: HelpCircle, segment: "faqs" },
      { href: "/content/tips", label: "Tips", icon: Lightbulb, segment: "tips" },
    ],
  },
  {
    title: "System",
    items: [
      { href: "/models", label: "Models", icon: Cpu, segment: "models" },
      { href: "/notifications", label: "Notifications", icon: Bell, segment: "notifications" },
      { href: "/settings", label: "Settings", icon: Settings, segment: "settings" },
      { href: "/profile", label: "Profile", icon: UserCircle, segment: "profile" },
    ],
  },
];

export function sectionFor(segment: string) {
  return NAV_GROUPS.find((g) => g.items.some((i) => i.segment === segment));
}

export const ALL_ITEMS = NAV_GROUPS.flatMap((g) => g.items);

export function findItem(segment: string) {
  return ALL_ITEMS.find((i) => i.segment === segment);
}