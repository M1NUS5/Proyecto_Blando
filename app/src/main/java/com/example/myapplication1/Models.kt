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