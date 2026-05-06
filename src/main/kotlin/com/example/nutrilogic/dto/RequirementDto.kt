package com.example.nutrilogic.dto

// Внутри RecommendController или отдельный файл
data class RequirementDto(
    val nutrientName: String,
    val targetNorm: Double,      // целевая норма (обязательная)
    val minNorm: Double = 0.0,   // минимальная (опционально)
    val maxNorm: Double = 0.0,   // максимальная (опционально, 0 = нет ограничения)
    val unit: String? = null,
    val maxGramsPerProduct: Double = 500.0
)