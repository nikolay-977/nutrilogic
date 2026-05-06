package com.example.nutrilogic.dto

data class RecommendationItem(
    val product: com.example.nutrilogic.model.Product,
    val effectiveness: Double,
    val requiredGrams: Double?  // граммов продукта для покрытия нормы
)