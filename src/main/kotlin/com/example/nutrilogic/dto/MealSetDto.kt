package com.example.nutrilogic.dto

import com.example.nutrilogic.model.Product

data class MealSetDto(
    val id: Int,
    val items: List<MealItem>,  // продукт + количество граммов
    val totalCalories: Double
)

data class MealItem(
    val product: Product,
    val requiredGrams: Double,
    val nutrientName: String,
    val contributesNorm: Double  // сколько мг нутриента даёт указанное количество
)