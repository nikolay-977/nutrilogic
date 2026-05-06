package com.example.nutrilogic.dto

// Внутри RecommendController или отдельный файл
data class RequirementDto(
    val nutrientName: String,
    val dailyNorm: Double  // в мг
)