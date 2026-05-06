package com.example.nutrilogic.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// Новая структура для хранения значения и единицы измерения
@JsonIgnoreProperties(ignoreUnknown = true)
data class NutrientValue(
    val value: String,   // числовое значение как строка (например "15.2")
    val unit: String     // единица измерения (г, мг, мкг, ккал и т.п.)
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Product(
    val name: String,
    val url: String,
    val category: String = "",
    val nutrients: Map<String, NutrientValue>   // ключ - название нутриента
) {
    /**
     * Ищет значение нутриента по названию (без учёта регистра).
     * Возвращает Double (число из поля value) или null.
     */
    fun getNutrientValue(nutrientName: String): Double? {
        val normalizedSearch = nutrientName.trim().lowercase()
        val entry = nutrients.entries.find {
            it.key.lowercase() == normalizedSearch || it.key.lowercase().contains(normalizedSearch)
        }
        return entry?.value?.value?.replace(',', '.')?.toDoubleOrNull()
    }

    /**
     * Возвращает единицу измерения нутриента по названию.
     */
    fun getNutrientUnit(nutrientName: String): String? {
        val normalizedSearch = nutrientName.trim().lowercase()
        val entry = nutrients.entries.find {
            it.key.lowercase() == normalizedSearch || it.key.lowercase().contains(normalizedSearch)
        }
        return entry?.value?.unit?.takeIf { it.isNotBlank() }
    }

    /**
     * Получение калорийности (на 100г) в ккал.
     */
    fun getCalories(): Double? {
        val caloriesKeys = listOf("калорийность", "энергетическая ценность", "ккал")
        for (key in caloriesKeys) {
            val entry = nutrients.entries.find { it.key.lowercase().contains(key) }
            entry?.let {
                val value = it.value.value.replace(',', '.').toDoubleOrNull()
                if (value != null) return value
            }
        }
        return null
    }
}