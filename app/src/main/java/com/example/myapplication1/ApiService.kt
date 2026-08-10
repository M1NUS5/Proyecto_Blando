package com.example.myapplication1

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    /** Comprobacion ligera que se usa para despertar el servidor al abrir la app. */
    @GET("health")
    fun health(): Call<Map<String, String>>

    // -----------------------------------------------------------------------
    // Autenticacion
    // -----------------------------------------------------------------------

    @POST("login")
    fun login(
        @Body request: LoginRequest
    ): Call<LoginResponse>

    @POST("register")
    fun register(
        @Body request: RegisterRequest
    ): Call<Map<String, String>>

    // -----------------------------------------------------------------------
    // Entrenamientos
    // -----------------------------------------------------------------------

    @POST("activities")
    fun saveActivity(
        @Body request: ActivityRequest
    ): Call<ActivityResponse>

    @GET("activities/{userId}")
    fun getActivities(
        @Path("userId") userId: String
    ): Call<List<ActivityItem>>

    @DELETE("activities/{id}")
    fun deleteActivity(
        @Path("id") id: String
    ): Call<DeleteResponse>

    // -----------------------------------------------------------------------
    // Analisis nutricional y registro de comidas
    // -----------------------------------------------------------------------

    /** Envia la fotografia al backend, que la analiza con Gemini. */
    @POST("analyze-food")
    fun analyzeFood(
        @Body request: FoodAnalysisRequest
    ): Call<FoodAnalysisResponse>

    @POST("meals")
    fun saveMeal(
        @Body request: MealRequest
    ): Call<MealResponse>

    /** Sin [date] devuelve todas las comidas; con [date] solo las de ese dia. */
    @GET("meals/{userId}")
    fun getMeals(
        @Path("userId") userId: String,
        @Query("date") date: String? = null
    ): Call<List<MealItem>>

    /** Totales de calorias y macronutrientes de un dia. */
    @GET("meals/{userId}/summary")
    fun getMealSummary(
        @Path("userId") userId: String,
        @Query("date") date: String
    ): Call<MealSummaryResponse>

    @DELETE("meals/{id}")
    fun deleteMeal(
        @Path("id") id: String
    ): Call<DeleteResponse>
}
