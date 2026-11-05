package ph.edu.asiatech.goananas.data

/**
 * The six classes recognized by the CNN, in the same order as the model output
 * (alphabetical folder order used during training, see model_training/train_model.py).
 * The recommended actions are the same as those of the Streamlit version described in the paper.
 */
enum class PineappleClass(
    val label: String,
    val displayName: String,
    val localName: String,
    val partToPhotograph: String,
    val symptoms: String,
    val cause: String,
    val management: List<String>,
    val recommendedAction: String,
    /** days before the app reminds the user to re-check the plant; 0 = no re-check needed */
    val recheckDays: Int,
) {
    FRUIT_ROT(
        label = "fruit_rot",
        displayName = "Fruit Rot",
        localName = "Bulok na bunga",
        partToPhotograph = "Fruit",
        symptoms = "Dark brown, soft, water-soaked areas on the fruit surface; the flesh under the spot becomes brown and may smell sour.",
        cause = "Fungal infection (commonly Thielaviopsis or Fusarium species) that enters through wounds and spreads in wet conditions.",
        management = listOf(
            "Remove and bury or burn infected fruits away from the field.",
            "Avoid wounding fruits during weeding and harvesting.",
            "Apply approved bio-fungicides following the label.",
        ),
        recommendedAction = "Remove infected fruits immediately to prevent fungal spore dispersal. Apply approved bio-fungicides.",
        recheckDays = 7,
    ),
    HEALTHY(
        label = "healthy",
        displayName = "Healthy",
        localName = "Malusog",
        partToPhotograph = "Whole plant",
        symptoms = "Green, firm leaves with normal color; no rotting, wilting, or abnormal crowns.",
        cause = "No disease or disorder detected on the visible parts.",
        management = listOf(
            "Continue standard irrigation and balanced fertilization.",
            "Inspect the plants monthly, especially for mealybugs (kuto-kuto).",
        ),
        recommendedAction = "Crop Status: Optimal. Continue standard irrigation and balanced fertilization schedules.",
        recheckDays = 0,
    ),
    HEART_ROT(
        label = "heart_rot",
        displayName = "Heart Rot",
        localName = "Bulok sa puso",
        partToPhotograph = "Heart / center leaves",
        symptoms = "Young center leaves turn yellow to light brown; their bases become soft and water-soaked, and the center leaves can be pulled out easily.",
        cause = "Phytophthora species, favored by poorly drained soil and heavy rain (Joy & Sindhu, 2012).",
        management = listOf(
            "Remove and destroy the infected heart and whorl tissue.",
            "Improve field drainage and avoid planting in low, wet areas.",
            "Apply copper-based fungicides following the label.",
        ),
        recommendedAction = "Remove and destroy infected heart/whorl tissue. Apply copper-based fungicides and improve drainage.",
        recheckDays = 7,
    ),
    MEALYBUG_WILT(
        label = "mealybug_wilt",
        displayName = "Mealybug Wilt",
        localName = "Pagkalanta dahil sa kuto-kuto",
        partToPhotograph = "Leaves",
        symptoms = "Leaves turn reddish to yellow, the tips dry, and the leaves curl downward and wilt.",
        cause = "A virus spread by mealybugs (kuto-kuto), which are protected by ants (PlantVillage, n.d.).",
        management = listOf(
            "Quarantine affected plants and check the surrounding plants for mealybugs.",
            "Control ants that protect the mealybugs.",
            "Apply systemic insecticides or introduce natural predators.",
        ),
        recommendedAction = "Quarantine affected plants. Apply systemic insecticides or introduce natural predators.",
        recheckDays = 14,
    ),
    MULTIPLE_CROWN(
        label = "multiple_crown_disorder",
        displayName = "Multiple Crown Disorder",
        localName = "Maraming korona",
        partToPhotograph = "Crown",
        symptoms = "Two or more crowns grow on top of one fruit instead of a single crown.",
        cause = "A growth disorder linked to genetics, nutrient imbalance (calcium and zinc), and growing conditions.",
        management = listOf(
            "Monitor calcium and zinc levels through soil and foliar tests.",
            "Use planting materials from plants without the disorder.",
        ),
        recommendedAction = "Monitor calcium and zinc. Conduct routine soil and foliar tests.",
        recheckDays = 30,
    ),
    ROOT_ROT(
        label = "root_rot",
        displayName = "Root Rot",
        localName = "Bulok na ugat (pasik)",
        partToPhotograph = "Base and roots",
        symptoms = "Leaves turn yellow and dry from the tips; the roots and base of the stem rot, and the plant is easily pulled out.",
        cause = "Phytophthora species and root-feeding pests, favored by waterlogged soil.",
        management = listOf(
            "Improve field drainage immediately and avoid waterlogging.",
            "Remove badly affected plants.",
            "Treat the soil with fungicides following the label.",
        ),
        recommendedAction = "Improve field drainage immediately. Avoid waterlogging and treat soil with fungicides.",
        recheckDays = 7,
    );

    val isHealthy: Boolean get() = this == HEALTHY

    companion object {
        /** model output order */
        val modelOrder: List<PineappleClass> = listOf(FRUIT_ROT, HEALTHY, HEART_ROT, MEALYBUG_WILT, MULTIPLE_CROWN, ROOT_ROT)
        fun fromLabel(label: String): PineappleClass = entries.firstOrNull { it.label == label } ?: HEALTHY
    }
}

/** plant parts the user can choose when taking a photograph */
val PLANT_PARTS = listOf("Leaf", "Crown", "Heart / center", "Base / roots", "Fruit", "Whole plant")
