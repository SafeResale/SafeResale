"use client";

import { use, useState, useEffect } from "react";
import Link from "next/link";
import { 
  ArrowLeft, 
  MapPin, 
  Phone, 
  User, 
  ShieldCheck, 
  Bike, 
  CheckCircle2, 
  Clock, 
  Sparkles, 
  Smartphone,
  Cpu,
  Wifi,
  Camera,
  Battery,
  AlertTriangle,
  History,
  Send
} from "lucide-react";
import { useFetch } from "@/lib/use-fetch";
import { post } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "sonner";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function InspectionDetailPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const id = resolvedParams.id;

  const { data, loading, error, reload } = useFetch<{ inspection: any }>(`/inspections/${id}`);
  const inspection = data?.inspection;

  // Form State for Inspection Findings
  const [overallCondition, setOverallCondition] = useState<string>("good");
  const [batteryCondition, setBatteryCondition] = useState<string>("good");
  const [screenCondition, setScreenCondition] = useState<string>("good");
  const [bodyCondition, setBodyCondition] = useState<string>("good");
  const [cameraCondition, setCameraCondition] = useState<string>("good");
  const [connectivityOk, setConnectivityOk] = useState<boolean>(true);
  const [biometricsOk, setBiometricsOk] = useState<boolean>(true);
  const [selectedDefects, setSelectedDefects] = useState<string[]>([]);
  const [notes, setNotes] = useState<string>("");

  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [isSubmittingReport, setIsSubmittingReport] = useState(false);

  // Populate from existing findings if already submitted
  useEffect(() => {
    if (inspection?.findings) {
      const f = inspection.findings;
      if (f.overall_condition) setOverallCondition(f.overall_condition);
      if (f.battery_condition) setBatteryCondition(f.battery_condition);
      if (f.screen_condition) setScreenCondition(f.screen_condition);
      if (f.body_condition) setBodyCondition(f.body_condition);
      if (f.camera_condition) setCameraCondition(f.camera_condition);
      if (f.connectivity_ok !== undefined) setConnectivityOk(f.connectivity_ok);
      if (f.biometrics_ok !== undefined) setBiometricsOk(f.biometrics_ok);
      if (f.defects_found) setSelectedDefects(f.defects_found);
      if (f.notes) setNotes(f.notes);
    }
  }, [inspection]);

  // Compute live AI Score estimate
  const computeLiveScore = () => {
    const base: Record<string, number> = {
      excellent: 95,
      good: 82,
      fair: 60,
      poor: 35
    };
    let score = base[overallCondition] || 80;
    
    [batteryCondition, screenCondition, bodyCondition, cameraCondition].forEach(c => {
      if (c === "poor") score -= 10;
      else if (c === "fair") score -= 5;
    });

    if (!connectivityOk) score -= 12;
    if (!biometricsOk) score -= 8;
    score -= Math.min(25, selectedDefects.length * 6);

    return Math.max(10, Math.min(100, Math.round(score)));
  };

  const estimatedScore = computeLiveScore();

  // Progress update handler (delivery-app transitions)
  const handleProgressUpdate = async (newStatus: "travelling" | "arrived" | "in_progress") => {
    try {
      setIsUpdatingStatus(true);
      await post(`/inspections/${id}/progress`, { status: newStatus });
      toast.success(`Status updated to "${newStatus}". Notifications pushed.`);
      reload();
    } catch (err: any) {
      toast.error(err?.message || "Failed to update status");
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // Submit final report handler
  const handleSubmitReport = async () => {
    try {
      setIsSubmittingReport(true);
      const payload = {
        overall_condition: overallCondition,
        battery_condition: batteryCondition,
        screen_condition: screenCondition,
        body_condition: bodyCondition,
        camera_condition: cameraCondition,
        connectivity_ok: connectivityOk,
        biometrics_ok: biometricsOk,
        defects_found: selectedDefects,
        notes: notes,
        calculated_score: estimatedScore,
        photos: []
      };

      await post(`/inspections/${id}/submit-report`, payload);
      toast.success("Inspection report certified & AI Trust Score saved!");
      reload();
    } catch (err: any) {
      toast.error(err?.message || "Failed to submit inspection report");
    } finally {
      setIsSubmittingReport(false);
    }
  };

  const toggleDefect = (defect: string) => {
    if (selectedDefects.includes(defect)) {
      setSelectedDefects(selectedDefects.filter(d => d !== defect));
    } else {
      setSelectedDefects([...selectedDefects, defect]);
    }
  };

  const availableDefects = [
    "Hairline screen scratch",
    "Deep glass scratch",
    "Dent on corner frame",
    "Minor frame scuff",
    "Loose charging port",
    "Speaker audio distortion",
    "Rear camera lens scratch",
    "Battery health below 80%",
    "Replaced non-OEM display",
    "Water ingress sticker triggered"
  ];

  if (loading) {
    return (
      <div className="p-6 max-w-6xl mx-auto space-y-6">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-32 w-full" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Skeleton className="h-96 w-full" />
          <Skeleton className="h-96 w-full" />
        </div>
      </div>
    );
  }

  if (error || !inspection) {
    return (
      <div className="p-6 max-w-xl mx-auto text-center space-y-4">
        <p className="text-destructive font-semibold">Inspection task not found</p>
        <Button asChild variant="outline">
          <Link href="/inspections">Back to Queue</Link>
        </Button>
      </div>
    );
  }

  const isCompleted = inspection.status === "completed";
  const currentStatus = inspection.status;

  return (
    <div className="flex flex-col gap-6 min-w-0 p-4 lg:p-6 max-w-6xl mx-auto w-full">
      {/* Navigation & Header */}
      <div>
        <Link 
          href="/inspections" 
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground mb-3 transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Inspection Queue
        </Link>
        
        <PageHeader
          title={inspection.listing?.title || "Device Inspection"}
          description={`ID: ${inspection._id} • Requested on ${new Date(inspection.created_at * 1000).toLocaleDateString()}`}
          actions={
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-sm px-3 py-1 font-mono uppercase">
                {currentStatus.replace("_", " ")}
              </Badge>
              {isCompleted && (
                <Badge className="bg-emerald-500 text-white font-mono gap-1 text-sm px-3 py-1">
                  <Sparkles className="h-3.5 w-3.5" />
                  Score: {Math.round(inspection.score || estimatedScore)}/100
                </Badge>
              )}
            </div>
          }
        />
      </div>

      {/* Delivery-App Live Progress Bar / Workflow Stepper */}
      <Card className="border-border/80 bg-card/60 backdrop-blur-sm overflow-hidden">
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center justify-between">
            <span className="flex items-center gap-2">
              <Bike className="h-5 w-5 text-primary" />
              Live Delivery &amp; Inspection Flow
            </span>
            <span className="text-xs font-normal text-muted-foreground">
              Notifications pushed via FCM at each step
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-4 gap-2 text-center text-xs">
            <div className={`p-2.5 rounded-lg border ${
              ["assigned", "travelling", "arrived", "in_progress", "completed"].includes(currentStatus)
                ? "bg-primary/10 border-primary text-primary font-medium"
                : "bg-muted/40 border-border text-muted-foreground"
            }`}>
              <CheckCircle2 className="h-4 w-4 mx-auto mb-1" />
              1. Assigned
            </div>

            <div className={`p-2.5 rounded-lg border ${
              ["travelling", "arrived", "in_progress", "completed"].includes(currentStatus)
                ? "bg-primary/10 border-primary text-primary font-medium"
                : "bg-muted/40 border-border text-muted-foreground"
            }`}>
              <Bike className="h-4 w-4 mx-auto mb-1" />
              2. Travelling
            </div>

            <div className={`p-2.5 rounded-lg border ${
              ["arrived", "in_progress", "completed"].includes(currentStatus)
                ? "bg-primary/10 border-primary text-primary font-medium"
                : "bg-muted/40 border-border text-muted-foreground"
            }`}>
              <MapPin className="h-4 w-4 mx-auto mb-1" />
              3. Arrived
            </div>

            <div className={`p-2.5 rounded-lg border ${
              currentStatus === "completed"
                ? "bg-emerald-500/10 border-emerald-500 text-emerald-600 font-medium"
                : ["in_progress"].includes(currentStatus)
                ? "bg-primary/10 border-primary text-primary font-medium"
                : "bg-muted/40 border-border text-muted-foreground"
            }`}>
              <ShieldCheck className="h-4 w-4 mx-auto mb-1" />
              4. Verified
            </div>
          </div>

          {/* Quick Action Progression Controls */}
          {!isCompleted && (
            <div className="flex flex-wrap items-center gap-3 pt-2">
              {currentStatus === "assigned" && (
                <Button 
                  onClick={() => handleProgressUpdate("travelling")}
                  disabled={isUpdatingStatus}
                  className="gap-2 cursor-pointer bg-purple-600 hover:bg-purple-700 text-white"
                >
                  <Bike className="h-4 w-4" />
                  Start Travelling to Location 🛵
                </Button>
              )}

              {currentStatus === "travelling" && (
                <Button 
                  onClick={() => handleProgressUpdate("arrived")}
                  disabled={isUpdatingStatus}
                  className="gap-2 cursor-pointer bg-blue-600 hover:bg-blue-700 text-white"
                >
                  <MapPin className="h-4 w-4" />
                  Mark as Arrived at Location 📍
                </Button>
              )}

              {currentStatus === "arrived" && (
                <Button 
                  onClick={() => handleProgressUpdate("in_progress")}
                  disabled={isUpdatingStatus}
                  className="gap-2 cursor-pointer bg-indigo-600 hover:bg-indigo-700 text-white"
                >
                  <Cpu className="h-4 w-4" />
                  Begin Physical Hardware Testing ⚙️
                </Button>
              )}

              {currentStatus === "in_progress" && (
                <Badge variant="outline" className="text-amber-600 border-amber-500/30 bg-amber-500/10 py-1.5 px-3">
                  Testing in Progress — Fill findings checklist below to certify
                </Badge>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Device & Parties Info + Timeline */}
        <div className="space-y-6 lg:col-span-1">
          {/* Parties Info */}
          <Card className="border-border/60">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <User className="h-4 w-4 text-primary" />
                Contact &amp; Location Details
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-xs">
              <div className="p-2.5 rounded bg-muted/40 space-y-1">
                <div className="font-semibold text-foreground">Buyer Details</div>
                <div>{inspection.buyer?.name || "Buyer"}</div>
                {inspection.buyer?.phone && (
                  <div className="text-muted-foreground font-mono flex items-center gap-1">
                    <Phone className="h-3 w-3" /> {inspection.buyer.phone}
                  </div>
                )}
              </div>

              <div className="p-2.5 rounded bg-muted/40 space-y-1">
                <div className="font-semibold text-foreground">Seller Details</div>
                <div>{inspection.seller?.name || "Seller"}</div>
                {inspection.seller?.phone && (
                  <div className="text-muted-foreground font-mono flex items-center gap-1">
                    <Phone className="h-3 w-3" /> {inspection.seller.phone}
                  </div>
                )}
              </div>

              {inspection.listing?.address && (
                <div className="p-2.5 rounded bg-muted/40 space-y-1">
                  <div className="font-semibold text-foreground flex items-center gap-1">
                    <MapPin className="h-3 w-3 text-red-500" /> Inspection Address
                  </div>
                  <div className="text-muted-foreground">{inspection.listing.address}</div>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Timeline History */}
          <Card className="border-border/60">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <History className="h-4 w-4 text-primary" />
                Audit Trail &amp; Updates
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {inspection.timeline?.length > 0 ? (
                <div className="relative pl-4 space-y-4 before:absolute before:left-1 before:top-2 before:bottom-2 before:w-0.5 before:bg-border">
                  {inspection.timeline.map((event: any, idx: number) => (
                    <div key={idx} className="relative text-xs">
                      <div className="absolute -left-4 top-1 h-2 w-2 rounded-full bg-primary" />
                      <div className="font-medium text-foreground">{event.label}</div>
                      <div className="text-muted-foreground">{event.description}</div>
                      <div className="text-[10px] text-muted-foreground/70 mt-0.5">
                        {new Date(event.timestamp * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">No timeline events recorded.</p>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Physical Testing Checklist & AI Engine Scoring */}
        <div className="lg:col-span-2 space-y-6">
          <Card className="border-border/80">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg flex items-center gap-2">
                    <ShieldCheck className="h-5 w-5 text-emerald-500" />
                    Physical Inspection Checklist
                  </CardTitle>
                  <CardDescription>
                    Test hardware components and submit verified findings.
                  </CardDescription>
                </div>

                {/* Live AI Trust Score Badge */}
                <div className="text-right">
                  <div className="text-xs text-muted-foreground uppercase font-mono">Live AI Score</div>
                  <div className="text-2xl font-black text-emerald-600 dark:text-emerald-400 font-mono">
                    {estimatedScore}<span className="text-sm font-normal text-muted-foreground">/100</span>
                  </div>
                </div>
              </div>
            </CardHeader>

            <CardContent className="space-y-6">
              {/* Overall Condition */}
              <div className="space-y-2">
                <Label className="text-sm font-semibold">Overall Device Condition</Label>
                <RadioGroup 
                  value={overallCondition} 
                  onValueChange={setOverallCondition}
                  disabled={isCompleted}
                  className="grid grid-cols-2 sm:grid-cols-4 gap-2"
                >
                  {["excellent", "good", "fair", "poor"].map((opt) => (
                    <div key={opt} className="flex items-center space-x-2 border rounded-lg p-3 hover:bg-muted/40 cursor-pointer">
                      <RadioGroupItem value={opt} id={`cond-${opt}`} />
                      <Label htmlFor={`cond-${opt}`} className="capitalize cursor-pointer text-sm font-medium">
                        {opt}
                      </Label>
                    </div>
                  ))}
                </RadioGroup>
              </div>

              <Separator />

              {/* Sub-Components Health Checks */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Screen */}
                <div className="space-y-2 border rounded-lg p-3">
                  <Label className="text-xs font-semibold flex items-center gap-1.5">
                    <Smartphone className="h-3.5 w-3.5" /> Display &amp; Touch
                  </Label>
                  <RadioGroup 
                    value={screenCondition} 
                    onValueChange={setScreenCondition}
                    disabled={isCompleted}
                    className="flex gap-2"
                  >
                    {["good", "fair", "poor"].map(o => (
                      <div key={o} className="flex items-center space-x-1.5">
                        <RadioGroupItem value={o} id={`sc-${o}`} />
                        <Label htmlFor={`sc-${o}`} className="capitalize text-xs cursor-pointer">{o}</Label>
                      </div>
                    ))}
                  </RadioGroup>
                </div>

                {/* Battery */}
                <div className="space-y-2 border rounded-lg p-3">
                  <Label className="text-xs font-semibold flex items-center gap-1.5">
                    <Battery className="h-3.5 w-3.5" /> Battery Health
                  </Label>
                  <RadioGroup 
                    value={batteryCondition} 
                    onValueChange={setBatteryCondition}
                    disabled={isCompleted}
                    className="flex gap-2"
                  >
                    {["good", "fair", "poor"].map(o => (
                      <div key={o} className="flex items-center space-x-1.5">
                        <RadioGroupItem value={o} id={`bat-${o}`} />
                        <Label htmlFor={`bat-${o}`} className="capitalize text-xs cursor-pointer">{o}</Label>
                      </div>
                    ))}
                  </RadioGroup>
                </div>

                {/* Body / Housing */}
                <div className="space-y-2 border rounded-lg p-3">
                  <Label className="text-xs font-semibold flex items-center gap-1.5">
                    <Smartphone className="h-3.5 w-3.5" /> Body &amp; Frame
                  </Label>
                  <RadioGroup 
                    value={bodyCondition} 
                    onValueChange={setBodyCondition}
                    disabled={isCompleted}
                    className="flex gap-2"
                  >
                    {["good", "fair", "poor"].map(o => (
                      <div key={o} className="flex items-center space-x-1.5">
                        <RadioGroupItem value={o} id={`body-${o}`} />
                        <Label htmlFor={`body-${o}`} className="capitalize text-xs cursor-pointer">{o}</Label>
                      </div>
                    ))}
                  </RadioGroup>
                </div>

                {/* Camera */}
                <div className="space-y-2 border rounded-lg p-3">
                  <Label className="text-xs font-semibold flex items-center gap-1.5">
                    <Camera className="h-3.5 w-3.5" /> Camera &amp; Sensors
                  </Label>
                  <RadioGroup 
                    value={cameraCondition} 
                    onValueChange={setCameraCondition}
                    disabled={isCompleted}
                    className="flex gap-2"
                  >
                    {["good", "fair", "poor"].map(o => (
                      <div key={o} className="flex items-center space-x-1.5">
                        <RadioGroupItem value={o} id={`cam-${o}`} />
                        <Label htmlFor={`cam-${o}`} className="capitalize text-xs cursor-pointer">{o}</Label>
                      </div>
                    ))}
                  </RadioGroup>
                </div>
              </div>

              {/* Functional Sensors */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex items-center justify-between border rounded-lg p-3">
                  <div className="space-y-0.5">
                    <Label className="text-xs font-semibold flex items-center gap-1">
                      <Wifi className="h-3.5 w-3.5" /> Connectivity
                    </Label>
                    <p className="text-[11px] text-muted-foreground">WiFi, Bluetooth &amp; SIM work</p>
                  </div>
                  <Switch 
                    checked={connectivityOk} 
                    onCheckedChange={setConnectivityOk}
                    disabled={isCompleted}
                  />
                </div>

                <div className="flex items-center justify-between border rounded-lg p-3">
                  <div className="space-y-0.5">
                    <Label className="text-xs font-semibold flex items-center gap-1">
                      <Cpu className="h-3.5 w-3.5" /> Biometrics
                    </Label>
                    <p className="text-[11px] text-muted-foreground">Fingerprint / Face ID operational</p>
                  </div>
                  <Switch 
                    checked={biometricsOk} 
                    onCheckedChange={setBiometricsOk}
                    disabled={isCompleted}
                  />
                </div>
              </div>

              {/* Defect Checkbox Tags */}
              <div className="space-y-2">
                <Label className="text-sm font-semibold flex items-center gap-1.5">
                  <AlertTriangle className="h-4 w-4 text-amber-500" />
                  Identified Physical Defects
                </Label>
                <div className="flex flex-wrap gap-2 pt-1">
                  {availableDefects.map((defect) => {
                    const isSelected = selectedDefects.includes(defect);
                    return (
                      <button
                        type="button"
                        key={defect}
                        disabled={isCompleted}
                        onClick={() => toggleDefect(defect)}
                        className={`text-xs px-2.5 py-1.5 rounded-full border transition-all cursor-pointer ${
                          isSelected
                            ? "bg-destructive/10 text-destructive border-destructive font-medium"
                            : "bg-muted/30 hover:bg-muted text-muted-foreground border-border"
                        }`}
                      >
                        {isSelected ? "✕ " : "+ "}
                        {defect}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Inspector Notes */}
              <div className="space-y-2">
                <Label className="text-sm font-semibold">Inspector Clinical Notes</Label>
                <Textarea
                  placeholder="Provide objective on-site feedback for the buyer..."
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  disabled={isCompleted}
                  rows={3}
                />
              </div>
            </CardContent>

            {!isCompleted && (
              <CardFooter className="pt-4 border-t flex flex-col sm:flex-row items-center justify-between gap-4 bg-muted/20">
                <div className="text-xs text-muted-foreground">
                  Submitting generates AI trust score and updates listing for buyer purchase.
                </div>
                <Button 
                  onClick={handleSubmitReport}
                  disabled={isSubmittingReport}
                  className="w-full sm:w-auto gap-2 bg-emerald-600 hover:bg-emerald-700 text-white cursor-pointer"
                >
                  <Send className="h-4 w-4" />
                  {isSubmittingReport ? "Calculating AI Score..." : "Certify & Submit AI Report"}
                </Button>
              </CardFooter>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
