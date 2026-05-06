package com.example.nutrilogic.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Product(
    val name: String,
    val url: String,
    val category: String = "",
    val nutrients: Map<String, String>
) {
    /**
     * Ищет значение нутриента по названию.
     * Сначала ищет точное совпадение (без учёта регистра),
     * затем – содержит ли ключ название нутриента.
     */
    fun getNutrientValue(nutrientName: String): Double? {
        val normalizedSearch = nutrientName.trim().lowercase()
        // точное совпадение
        val exact = nutrients.entries.find { it.key.lowercase() == normalizedSearch }
        if (exact != null) return parseDouble(exact.value)
        // частичное совпадение
        val contains = nutrients.entries.find { it.key.lowercase().contains(normalizedSearch) }
        return contains?.let { parseDouble(it.value) }
    }

    fun getCalories(): Double? {
        val caloriesKeys = listOf("калорийность", "энергетическая ценность", "ккал")
        for (key in caloriesKeys) {
            val entry = nutrients.entries.find { it.key.lowercase().contains(key) }
            if (entry != null) {
                return parseDouble(entry.value)
            }
        }
        return null
    }

    private fun parseDouble(s: String): Double? {
        // Ищем число с возможной десятичной точкой или запятой
        val match = Regex("(\\d+[.,]?\\d*)").find(s)
        return match?.value?.replace(',', '.')?.toDoubleOrNull()
    }
}