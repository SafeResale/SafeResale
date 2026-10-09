package com.saferesale.app.domain.model

/**
 * Category-aware defect taxonomy — mirrors backend/app/core/catalog.py and
 * ml/m1_defect_detection/scripts/assess_device.py:SEVERITY (29 classes).
 * Keep in sync.
 */
object Defects {

    val LABELS: Map<String, String> = mapOf(
        "scratch" to "Scratch", "crack" to "Crack", "dent" to "Dent",
        "screen_damage" to "Screen damage", "glass_damage" to "Glass damage",
        "camera_damage" to "Camera damage", "port_damage" to "Port damage",
        "casing_damage" to "Casing damage", "body_deformation" to "Body deformation",
        "paint_damage" to "Paint damage", "chip" to "Chip", "rust" to "Rust",
        "corrosion" to "Corrosion", "water_damage" to "Water damage",
        "stain" to "Stain", "discoloration" to "Discoloration", "wear" to "Wear",
        "broken_part" to "Broken part", "missing_part" to "Missing part",
        "button_damage" to "Button damage", "keyboard_damage" to "Keyboard damage",
        "hinge_damage" to "Hinge damage", "cable_damage" to "Cable damage",
        "connector_damage" to "Connector damage", "tire_damage" to "Tire damage",
        "wheel_damage" to "Wheel damage", "mirror_damage" to "Mirror damage",
        "light_damage" to "Light damage", "bumper_damage" to "Bumper damage"
    )

    val BY_CATEGORY: Map<String, List<String>> = mapOf(
        "mobile" to listOf("scratch", "crack", "dent", "screen_damage", "glass_damage", "camera_damage", "port_damage", "casing_damage", "chip", "paint_damage", "stain", "discoloration", "wear", "water_damage", "corrosion"),
        "laptop" to listOf("scratch", "crack", "dent", "screen_damage", "glass_damage", "keyboard_damage", "hinge_damage", "port_damage", "casing_damage", "paint_damage", "stain", "discoloration", "wear", "chip"),
        "electronics" to listOf("scratch", "dent", "screen_damage", "glass_damage", "port_damage", "cable_damage", "connector_damage", "stain", "discoloration", "wear", "chip", "crack"),
        "camera" to listOf("scratch", "crack", "dent", "glass_damage", "paint_damage", "discoloration", "wear", "chip", "body_deformation", "stain"),
        "gaming" to listOf("scratch", "crack", "dent", "button_damage", "port_damage", "casing_damage", "stain", "discoloration", "wear", "chip"),
        "appliance" to listOf("scratch", "dent", "rust", "corrosion", "water_damage", "stain", "discoloration", "wear", "chip", "crack", "broken_part", "missing_part", "cable_damage"),
        "furniture" to listOf("scratch", "dent", "stain", "water_damage", "discoloration", "wear", "chip", "crack", "broken_part", "missing_part", "hinge_damage", "rust"),
        "car" to listOf("scratch", "dent", "paint_damage", "body_deformation", "chip", "rust", "corrosion", "glass_damage", "light_damage", "bumper_damage", "tire_damage", "wheel_damage", "mirror_damage", "water_damage", "stain", "discoloration", "wear", "crack"),
        "bike" to listOf("scratch", "dent", "paint_damage", "rust", "corrosion", "crack", "tire_damage", "wheel_damage", "body_deformation", "chip", "stain", "discoloration", "wear", "cable_damage", "light_damage"),
        "accessory" to listOf("scratch", "crack", "stain", "discoloration", "wear", "broken_part", "missing_part", "cable_damage", "connector_damage", "chip"),
        "vehicle" to listOf("scratch", "dent", "paint_damage", "body_deformation", "chip", "rust", "corrosion", "glass_damage", "light_damage", "bumper_damage", "tire_damage", "wheel_damage", "mirror_damage", "water_damage", "stain", "discoloration", "wear", "crack"),
        "tablet" to listOf("scratch", "crack", "dent", "screen_damage", "glass_damage", "port_damage", "casing_damage", "chip", "stain", "discoloration", "wear")
    )

    fun label(cls: String): String = LABELS[cls] ?: cls.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

    fun forCategory(category: String?): List<String> =
        if (category == null) LABELS.keys.toList() else BY_CATEGORY[category] ?: LABELS.keys.toList()

    fun isRelevant(category: String?, cls: String): Boolean {
        if (category == null) return true
        return BY_CATEGORY[category]?.contains(cls) ?: true
    }
}
