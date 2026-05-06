package com.example.nutrilogic.model

import java.time.LocalDate

data class UserProfile(
    var name: String = "",
    var gender: String = "male",
    var birthDate: LocalDate? = null,
    var height: Double = 0.0,
    var weight: Double = 0.0,
    var activityLevel: String = "moderate",
    var targetCalories: Double = 2000.0,
    var targetProtein: Double = 120.0,
    var targetFat: Double = 70.0,
    var targetCarbs: Double = 250.0,
    // Произвольные нутриенты: ключ - название, значение - тройка (цель, минимум, максимум)
    var customTargets: MutableMap<String, Triple<Double, Double, Double>> = mutableMapOf(),
    var favoriteProducts: MutableSet<String> = mutableSetOf(),
    var bannedProducts: MutableSet<String> = mutableSetOf()
) {
    fun isFavorite(productName: String): Boolean = favoriteProducts.contains(productName)
    fun isBanned(productName: String): Boolean = bannedProducts.contains(productName)
}