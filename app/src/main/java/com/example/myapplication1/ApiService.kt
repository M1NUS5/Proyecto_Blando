package com.example.myapplication1

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("login")
    fun login(
        @Body request: LoginRequest
    ): Call<LoginResponse>

    @POST("register")
    fun register(
        @Body request: RegisterRequest
    ): Call<Map<String, String>>

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
}