package com.example.nutrilogic.model

import java.time.LocalDate

data class DiaryEntry(
    val date: LocalDate,
    val meals: MutableMap<String, MutableList<ConsumedProduct>> = mutableMapOf(
        "breakfast" to mutableListOf(),
        "lunch" to mutableListOf(),
        "dinner" to mutableListOf(),
        "snack" to mutableListOf()
    )
) {
    fun totalCalories(): Double = meals.values.flatten().sumOf { it.calories }
    fun totalNutrient(nutrientName: String): Double = meals.values.flatten().sumOf { it.nutrients[nutrientName] ?: 0.0 }
}

data class ConsumedProduct(
    val product: Product,
    val quantity: Double,
    val mealType: String,
    val calories: Double,
    val nutrients: Map<String, Double>
)