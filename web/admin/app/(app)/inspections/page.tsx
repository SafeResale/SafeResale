"use client";

import { useState } from "react";
import Link from "next/link";
import { 
  ClipboardCheck, 
  MapPin, 
  Phone, 
  User, 
  Clock, 
  ArrowRight, 
  ShieldCheck, 
  Bike, 
  Sparkles,
  RefreshCw,
  Search,
  CheckCircle2,
  AlertCircle
} from "lucide-react";
import { useFetch } from "@/lib/use-fetch";
import { post } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "sonner";

interface InspectionItem {
  _id: string;
  listing_id: string;
  buyer_id?: string;
  seller_id?: string;
  inspector_id?: string;
  status: "requested" | "assigned" | "travelling" | "arrived" | "in_progress" | "completed" | "cancelled" | "rejected";
  preferred_date?: string;
  note?: string;
  created_at: number;
  updated_at?: number;
  score?: number;
  findings?: {
    verdict?: string;
    score?: number;
    overall_condition?: string;
  };
  listing?: {
    id: string;
    title: string;
    category?: string;
    price?: number;
    brand?: string;
    model?: string;
    address?: string;
    latitude?: number;
    longitude?: number;
  };
  buyer?: {
    id: string;
    name: string;
    phone?: string;
    email?: string;
  };
  seller?: {
    id: string;
    name: string;
    phone?: string;
    email?: string;
  };
  inspector?: {
    id: string;
    name: string;
  };
  requested_by_admin?: boolean;
}

/**
 * Digits-only phone, country code stripped: "+918792137157" -> "8792137157".
 * Inspectors call this number to coordinate, so it is the primary identifier.
 */
function digitsOnly(phone?: string | null): string | null {
  if (!phone) return null;
  const d = phone.replace(/\D/g, "");
  return d.length > 10 ? d.slice(-10) : d || null;
}

export default function InspectionsPage() {
  const { data, loading, error, reload } = useFetch<{ items: InspectionItem[]; total: number }>("/inspections");
  const [activeTab, setActiveTab] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [acceptingId, setAcceptingId] = useState<string | null>(null);

  const items = data?.items || [];

  const handleAccept = async (id: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      setAcceptingId(id);
      await post(`/inspections/${id}/accept`);
      toast.success("Inspection task accepted! Notification sent to buyer.");
      reload();
    } catch (err: any) {
      toast.error(err?.message || "Failed to accept inspection");
    } finally {
      setAcceptingId(null);
    }
  };

  const filteredItems = items.filter((item) => {
    if (activeTab === "pending" && item.status !== "requested") return false;
    if (activeTab === "active" && !["assigned", "travelling", "arrived", "in_progress"].includes(item.status)) return false;
    if (activeTab === "completed" && item.status !== "completed") return false;

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const title = item.listing?.title?.toLowerCase() || "";
      const buyer = item.buyer?.name?.toLowerCase() || "";
      const address = item.listing?.address?.toLowerCase() || "";
      return title.includes(q) || buyer.includes(q) || address.includes(q);
    }
    return true;
  });

  const pendingCount = items.filter((i) => i.status === "requested").length;
  const activeCount = items.filter((i) => ["assigned", "travelling", "arrived", "in_progress"].includes(i.status)).length;
  const completedCount = items.filter((i) => i.status === "completed").length;

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "requested":
        return <Badge variant="secondary" className="bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20">Pending Assignment</Badge>;
      case "assigned":
        return <Badge variant="secondary" className="bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20">Inspector Assigned</Badge>;
      case "travelling":
        return <Badge variant="secondary" className="bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20">En Route 🛵</Badge>;
      case "arrived":
      case "in_progress":
        return <Badge variant="secondary" className="bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20">Testing in Progress ⚙️</Badge>;
      case "completed":
        return <Badge variant="secondary" className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20">Verified & Scored ✅</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  return (
    <div className="flex flex-col gap-6 min-w-0 p-4 lg:p-6 max-w-7xl mx-auto w-full">
      <PageHeader
        title="Physical Device Inspections"
        description="Verify second-hand items on-site, test hardware condition, and compute AI trust scores."
        actions={
          <Button variant="outline" size="sm" onClick={() => reload()} className="gap-2 cursor-pointer">
            <RefreshCw className="h-4 w-4" />
            Refresh Queue
          </Button>
        }
      />

      {/* KPI Stats Summary */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="border-border/60 bg-card/60 backdrop-blur-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Pending Requests</CardTitle>
            <Clock className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">{pendingCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Awaiting inspector pickup</p>
          </CardContent>
        </Card>

        <Card className="border-border/60 bg-card/60 backdrop-blur-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">In Progress</CardTitle>
            <Bike className="h-4 w-4 text-purple-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">{activeCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Travelling or testing on-site</p>
          </CardContent>
        </Card>

        <Card className="border-border/60 bg-card/60 backdrop-blur-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Completed Audits</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">{completedCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Certified with AI score</p>
          </CardContent>
        </Card>
      </div>

      {/* Tabs & Search Filter */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full sm:w-auto">
          <TabsList className="grid grid-cols-4 w-full sm:w-auto">
            <TabsTrigger value="all" className="cursor-pointer">All ({items.length})</TabsTrigger>
            <TabsTrigger value="pending" className="cursor-pointer">Pending ({pendingCount})</TabsTrigger>
            <TabsTrigger value="active" className="cursor-pointer">Active ({activeCount})</TabsTrigger>
            <TabsTrigger value="completed" className="cursor-pointer">Completed ({completedCount})</TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search device, buyer, area..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {/* Queue Content */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Card key={i} className="p-6 space-y-4">
              <Skeleton className="h-6 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-10 w-full" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="p-8 text-center border-dashed">
          <AlertCircle className="mx-auto h-8 w-8 text-destructive mb-3" />
          <h3 className="font-semibold text-lg">Failed to load inspection queue</h3>
          <p className="text-sm text-muted-foreground mt-1">{error.message}</p>
          <Button onClick={() => reload()} variant="outline" className="mt-4 cursor-pointer">
            Retry
          </Button>
        </Card>
      ) : filteredItems.length === 0 ? (
        <Card className="p-12 text-center border-dashed">
          <ClipboardCheck className="mx-auto h-12 w-12 text-muted-foreground/60 mb-3" />
          <h3 className="font-semibold text-lg">No inspection requests found</h3>
          <p className="text-sm text-muted-foreground mt-1">
            {searchQuery ? "No matches found for your search query." : "When buyers request on-site device inspections, they will appear here."}
          </p>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredItems.map((item) => {
            const isPending = item.status === "requested";
            const isCompleted = item.status === "completed";
            const score = item.score || item.findings?.score;
            const buyerPhone = digitsOnly(item.buyer?.phone);
            const buyerName = item.buyer?.name?.trim();

            return (
              <Card key={item._id} className="flex flex-col justify-between overflow-hidden border-border/80 hover:border-primary/40 transition-all hover:shadow-md">
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between gap-2 mb-1.5">
                    {getStatusBadge(item.status)}
                    {score !== undefined && (
                      <Badge className="bg-emerald-500 hover:bg-emerald-600 text-white font-mono gap-1">
                        <Sparkles className="h-3 w-3" />
                        Score: {Math.round(score)}/100
                      </Badge>
                    )}
                  </div>
                  <CardTitle className="text-lg line-clamp-1">
                    {item.listing?.title || "Device Inspection"}
                  </CardTitle>
                  <CardDescription className="flex items-center gap-1.5 text-xs">
                    <span>₹{item.listing?.price?.toLocaleString() || "N/A"}</span>
                    <span>•</span>
                    <span className="capitalize">{item.listing?.category || "Electronics"}</span>
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-3 pb-3 text-sm">
                  {/* Requester — phone first, since the inspector calls them */}
                  <div className="rounded-lg bg-muted/40 p-3 space-y-2 border border-border/40">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-muted-foreground flex items-center gap-1">
                        <User className="h-3.5 w-3.5" /> Requested by:
                      </span>
                      {buyerPhone ? (
                        <a
                          href={`tel:${buyerPhone}`}
                          className="font-mono font-semibold text-foreground hover:text-primary hover:underline"
                        >
                          {buyerPhone}
                        </a>
                      ) : item.requested_by_admin ? (
                        <span className="font-medium">Admin / Moderator</span>
                      ) : (
                        <span className="font-medium text-muted-foreground">—</span>
                      )}
                    </div>
                    {(buyerName || buyerPhone) && (
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-muted-foreground flex items-center gap-1">
                          <Phone className="h-3.5 w-3.5" /> Name:
                        </span>
                        <span className="font-medium">
                          {buyerName || "—"}
                          {buyerPhone ? (
                            <a
                              href={`tel:${buyerPhone}`}
                              className="ml-2 font-mono font-normal text-muted-foreground hover:text-primary hover:underline"
                              title="Call requester"
                            >
                              Call
                            </a>
                          ) : null}
                        </span>
                      </div>
                    )}
                    {item.requested_by_admin && (
                      <p className="text-[11px] text-muted-foreground leading-snug">
                        Raised by an admin during moderation — no buyer account attached.
                      </p>
                    )}
                    {item.seller?.phone && (
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-muted-foreground flex items-center gap-1">
                          <Phone className="h-3.5 w-3.5" /> Seller:
                        </span>
                        <span className="font-mono">{digitsOnly(item.seller.phone)}</span>
                      </div>
                    )}
                    {item.listing?.address && (
                      <div className="flex items-start justify-between text-xs pt-1 border-t border-border/30">
                        <span className="text-muted-foreground flex items-center gap-1 shrink-0">
                          <MapPin className="h-3.5 w-3.5" /> Location:
                        </span>
                        <span className="text-right truncate ml-2 max-w-[180px]">{item.listing.address}</span>
                      </div>
                    )}
                  </div>

                  {item.note && (
                    <p className="text-xs text-muted-foreground italic bg-amber-500/5 border border-amber-500/10 p-2 rounded">
                      Buyer Note: &quot;{item.note}&quot;
                    </p>
                  )}
                </CardContent>

                <CardFooter className="pt-2 border-t border-border/40 bg-muted/10">
                  {isPending ? (
                    <Button 
                      className="w-full gap-2 cursor-pointer"
                      onClick={(e) => handleAccept(item._id, e)}
                      disabled={acceptingId === item._id}
                    >
                      {acceptingId === item._id ? (
                        <>
                          <RefreshCw className="h-4 w-4 animate-spin" />
                          Accepting Task...
                        </>
                      ) : (
                        <>
                          <ShieldCheck className="h-4 w-4" />
                          Accept Inspection Job
                        </>
                      )}
                    </Button>
                  ) : (
                    <Button asChild variant={isCompleted ? "outline" : "default"} className="w-full gap-2 cursor-pointer">
                      <Link href={`/inspections/${item._id}`}>
                        {isCompleted ? "View Full Report" : "Open Inspection Portal"}
                        <ArrowRight className="h-4 w-4" />
                      </Link>
                    </Button>
                  )}
                </CardFooter>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
