package com.example.myapplication1

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class User(
    val _id: String,
    val name: String,
    val email: String
)

data class ActivityRequest(
    val userId: String,
    val title: String,
    val date: String,
    val distance: Double,
    val duration: Double,
    val pace: Double,
    val notes: String,

    val bpm: Int = 0,
    val steps: Int = 0,
    val cadence: Double = 0.0,
    val acceleration: Double = 0.0,

    val iaClass: Int? = null,
    val iaLabel: String = "",
    val iaConfidence: Double = 0.0,
    val iaRecommendation: String = ""
)

data class ActivityResponse(
    val message: String,
    val activity: ActivityItem
)

data class DeleteResponse(
    val message: String = ""
)

data class ActivityItem(
    val _id: String? = null,
    val userId: String = "",
    val title: String = "",
    val date: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val pace: Double = 0.0,
    val notes: String = "",

    val bpm: Int = 0,
    val steps: Int = 0,
    val cadence: Double = 0.0,
    val acceleration: Double = 0.0,

    val iaClass: Int? = null,
    val iaLabel: String = "",
    val iaConfidence: Double = 0.0,
    val iaRecommendation: String = ""
)

// ---------------------------------------------------------------------------
// Analisis nutricional de comidas
// ---------------------------------------------------------------------------

/**
 * Contexto del usuario que acompana a la fotografia para personalizar el consejo.
 * Las metas van ya calculadas: al modelo se le pide interpretar cifras, no
 * hacer aritmetica.
 */
data class PerfilNutricional(
    val edad: Int = 0,
    val sexo: String = "",
    val estaturaCm: Int = 0,
    val pesoKg: Double = 0.0,
    val nivelActividad: String = "",
    val objetivo: String = "",

    val caloriasMeta: Int = 0,
    val proteinaMeta: Int = 0,
    val carbosMeta: Int = 0,
    val grasasMeta: Int = 0,

    val caloriasConsumidas: Int = 0,
    val proteinaConsumida: Int = 0,
    val carbosConsumidos: Int = 0,
    val grasasConsumidas: Int = 0
)

data class FoodAnalysisRequest(
    val imageBase64: String,
    val mimeType: String,
    /** Ausente mientras el usuario no haya llenado sus datos corporales. */
    val perfil: PerfilNutricional? = null
)

data class FoodAnalysisResponse(
    val isFood: Boolean = false,
    val foodName: String = "",
    val category: String = "",
    val portion: String = "",
    val estimatedCalories: Int = 0,
    val calorieRange: String = "",
    val protein: Int = 0,
    val carbs: Int = 0,
    val fats: Int = 0,
    val confidence: String = "",
    val observation: String = "",
    /** Consejo redactado por la IA. Vacio si no se envio el perfil. */
    val recomendacion: String = "",
    /** Porcion que la IA sugiere para este usuario, si difiere de la analizada. */
    val porcionSugerida: String = ""
)

data class MealRequest(
    val userId: String,
    val foodName: String,
    val category: String,
    val portion: String,
    val estimatedCalories: Int,
    val calorieRange: String,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
    val confidence: String,
    val observation: String,
    val date: String
)

data class MealResponse(
    val message: String = "",
    val meal: MealItem? = null
)

data class MealItem(
    val _id: String? = null,
    val userId: String = "",
    val foodName: String = "",
    val category: String = "",
    val portion: String = "",
    val estimatedCalories: Int = 0,
    val calorieRange: String = "",
    val protein: Int = 0,
    val carbs: Int = 0,
    val fats: Int = 0,
    val confidence: String = "",
    val observation: String = "",
    val date: String = "",
    val createdAt: String = ""
)

data class MealSummaryResponse(
    val date: String = "",
    val cantidadComidas: Int = 0,
    val totalCalories: Int = 0,
    val totalProtein: Int = 0,
    val totalCarbs: Int = 0,
    val totalFats: Int = 0
)