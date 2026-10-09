/** Category-aware defect taxonomy — mirrors backend/app/core/catalog.py and
 * ml/m1_defect_detection/scripts/assess_device.py:SEVERITY.
 * 14 core + 15 extensions per docs/08-ml-plan.md §3.1 (29 total).
 * Keep in sync with backend; single source of truth is backend/app/core/catalog.py
 */

export const DEFECT_LABELS: Record<string, string> = {
  scratch: "Scratch",
  crack: "Crack",
  dent: "Dent",
  screen_damage: "Screen damage",
  glass_damage: "Glass damage",
  camera_damage: "Camera damage",
  port_damage: "Port damage",
  casing_damage: "Casing damage",
  body_deformation: "Body deformation",
  paint_damage: "Paint damage",
  chip: "Chip",
  rust: "Rust",
  corrosion: "Corrosion",
  water_damage: "Water damage",
  stain: "Stain",
  discoloration: "Discoloration",
  wear: "Wear",
  broken_part: "Broken part",
  missing_part: "Missing part",
  button_damage: "Button damage",
  keyboard_damage: "Keyboard damage",
  hinge_damage: "Hinge damage",
  cable_damage: "Cable damage",
  connector_damage: "Connector damage",
  tire_damage: "Tire damage",
  wheel_damage: "Wheel damage",
  mirror_damage: "Mirror damage",
  light_damage: "Light damage",
  bumper_damage: "Bumper damage",
};

export const DEFECTS_BY_CATEGORY: Record<string, string[]> = {
  mobile: ["scratch", "crack", "dent", "screen_damage", "glass_damage", "camera_damage", "port_damage", "casing_damage", "chip", "paint_damage", "stain", "discoloration", "wear", "water_damage", "corrosion"],
  laptop: ["scratch", "crack", "dent", "screen_damage", "glass_damage", "keyboard_damage", "hinge_damage", "port_damage", "casing_damage", "paint_damage", "stain", "discoloration", "wear", "chip"],
  electronics: ["scratch", "dent", "screen_damage", "glass_damage", "port_damage", "cable_damage", "connector_damage", "stain", "discoloration", "wear", "chip", "crack"],
  camera: ["scratch", "crack", "dent", "glass_damage", "paint_damage", "discoloration", "wear", "chip", "body_deformation", "stain"],
  gaming: ["scratch", "crack", "dent", "button_damage", "port_damage", "casing_damage", "stain", "discoloration", "wear", "chip"],
  appliance: ["scratch", "dent", "rust", "corrosion", "water_damage", "stain", "discoloration", "wear", "chip", "crack", "broken_part", "missing_part", "cable_damage"],
  furniture: ["scratch", "dent", "stain", "water_damage", "discoloration", "wear", "chip", "crack", "broken_part", "missing_part", "hinge_damage", "rust"],
  car: ["scratch", "dent", "paint_damage", "body_deformation", "chip", "rust", "corrosion", "glass_damage", "light_damage", "bumper_damage", "tire_damage", "wheel_damage", "mirror_damage", "water_damage", "stain", "discoloration", "wear", "crack"],
  bike: ["scratch", "dent", "paint_damage", "rust", "corrosion", "crack", "tire_damage", "wheel_damage", "body_deformation", "chip", "stain", "discoloration", "wear", "cable_damage", "light_damage"],
  accessory: ["scratch", "crack", "stain", "discoloration", "wear", "broken_part", "missing_part", "cable_damage", "connector_damage", "chip"],
  vehicle: ["scratch", "dent", "paint_damage", "body_deformation", "chip", "rust", "corrosion", "glass_damage", "light_damage", "bumper_damage", "tire_damage", "wheel_damage", "mirror_damage", "water_damage", "stain", "discoloration", "wear", "crack"],
  tablet: ["scratch", "crack", "dent", "screen_damage", "glass_damage", "port_damage", "casing_damage", "chip", "stain", "discoloration", "wear"],
};

export function defectLabel(cls: string): string {
  return DEFECT_LABELS[cls] ?? cls.replace(/[_-]+/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export function defectsFor(category?: string | null): string[] {
  if (!category) return Object.keys(DEFECT_LABELS);
  return DEFECTS_BY_CATEGORY[category] ?? Object.keys(DEFECT_LABELS);
}

export function isRelevantDefect(category: string | null | undefined, defectClass: string): boolean {
  if (!category) return true;
  const allowed = DEFECTS_BY_CATEGORY[category];
  if (!allowed) return true;
  return allowed.includes(defectClass);
}

// Family grouping for UI chips (surface / structural / exterior etc.)
export const DEFECT_FAMILY: Record<string, string> = {
  scratch: "Surface", crack: "Surface", dent: "Surface", paint_damage: "Surface", chip: "Surface",
  screen_damage: "Display", glass_damage: "Display",
  rust: "Chemical", corrosion: "Chemical", water_damage: "Chemical",
  body_deformation: "Structural", casing_damage: "Structural", broken_part: "Structural", missing_part: "Structural", bumper_damage: "Structural", hinge_damage: "Structural",
  camera_damage: "Optical",
  port_damage: "Functional", button_damage: "Functional", keyboard_damage: "Functional", cable_damage: "Functional", connector_damage: "Functional",
  stain: "Cosmetic", discoloration: "Cosmetic", wear: "Cosmetic",
  tire_damage: "Mechanical", wheel_damage: "Mechanical",
  mirror_damage: "Exterior", light_damage: "Exterior",
};

export function defectFamily(cls: string): string {
  return DEFECT_FAMILY[cls] ?? "Other";
}
