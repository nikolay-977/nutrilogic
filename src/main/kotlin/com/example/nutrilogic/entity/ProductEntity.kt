package com.example.nutrilogic.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "products")
class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    var name: String,

    @Column(nullable = false)
    var url: String,

    var category: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    var nutrients: MutableMap<String, NutrientValue> = mutableMapOf()
) {
    // Вложенный класс для JSON
    data class NutrientValue(
        val value: String,
        val unit: String
    )

    // Метод для получения числового значения нутриента по названию
    fun getNutrientValue(nutrientName: String): Double? {
        val normalizedSearch = nutrientName.trim().lowercase()
        val entry = nutrients.entries.find {
            it.key.lowercase() == normalizedSearch || it.key.lowercase().contains(normalizedSearch)
        }
        return entry?.value?.value?.replace(',', '.')?.toDoubleOrNull()
    }

    // Метод для получения калорийности (на 100 г) в ккал
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