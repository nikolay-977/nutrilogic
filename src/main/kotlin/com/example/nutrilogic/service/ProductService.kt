package com.example.nutrilogic.service

import com.example.nutrilogic.model.Product
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.io.File

@Service
class ProductService {

    private val products = mutableListOf<Product>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @EventListener(ApplicationReadyEvent::class)
    fun loadProducts() {
        val dataDir = File("data/full_products")
        if (!dataDir.exists()) {
            println("⚠️ Папка data/full_products не найдена! Сначала запустите парсер.")
            return
        }
        dataDir.listFiles { file -> file.extension == "json" }?.forEach { jsonFile ->
            try {
                val product = objectMapper.readValue(jsonFile, Product::class.java)
                products.add(product)
            } catch (e: Exception) {
                println("Ошибка чтения ${jsonFile.name}: ${e.message}")
            }
        }
        println("✅ Загружено ${products.size} продуктов")
    }

    // Возвращает ВСЕ продукты, отсортированные по эффективности
    fun recommendProductsAll(nutrientQuery: String): List<Pair<Product, Double>> {
        val normalizedQuery = nutrientQuery.trim().lowercase()
        if (products.isEmpty()) return emptyList()

        var targetKey: String? = null
        for (product in products) {
            targetKey = product.nutrients.keys.find {
                it.lowercase() == normalizedQuery || it.lowercase().contains(normalizedQuery)
            }
            if (targetKey != null) break
        }
        if (targetKey == null) return emptyList()

        val result = mutableListOf<Pair<Product, Double>>()
        for (product in products) {
            val value = product.getNutrientValue(targetKey) ?: continue
            val calories = product.getCalories() ?: continue
            if (calories <= 0) continue
            result.add(product to (value / calories))
        }
        return result.sortedByDescending { it.second }
    }

    // Возвращает ВСЕ продукты с фильтром по категории
    fun recommendProductsAll(nutrientQuery: String, category: String? = null): List<Pair<Product, Double>> {
        val normalizedQuery = nutrientQuery.trim().lowercase()
        if (products.isEmpty()) return emptyList()

        var targetKey: String? = null
        for (product in products) {
            targetKey = product.nutrients.keys.find {
                it.lowercase() == normalizedQuery || it.lowercase().contains(normalizedQuery)
            }
            if (targetKey != null) break
        }
        if (targetKey == null) return emptyList()

        val result = mutableListOf<Pair<Product, Double>>()
        for (product in products) {
            // Фильтр по категории
            if (category != null && category.isNotBlank() && product.category != category) continue
            val value = product.getNutrientValue(targetKey) ?: continue
            val calories = product.getCalories() ?: continue
            if (calories <= 0) continue
            result.add(product to (value / calories))
        }
        return result.sortedByDescending { it.second }
    }

    fun getAllCategoryNames(): List<String> {
        return products.map { it.category }.distinct().sorted()
    }

    fun searchProducts(nameQuery: String?, category: String?): MutableList<Product> {
        var result = products
        if (!nameQuery.isNullOrBlank()) {
            val lowerQuery = nameQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(lowerQuery) }.toMutableList()
        }
        if (!category.isNullOrBlank()) {
            result = result.filter { it.category == category }.toMutableList()
        }
        return result.sortedBy { it.name }.toMutableList()
    }

    fun recommendProducts(nutrientQuery: String, limit: Int = 7): List<Pair<Product, Double>> {
        // Нормализуем запрос: убираем лишние пробелы, приводим к нижнему регистру
        val normalizedQuery = nutrientQuery.trim().lowercase()

        // Сначала попробуем найти точное совпадение (игнорируя регистр)
        var exactKey: String? = null
        if (products.isNotEmpty()) {
            val sampleKeys = products.first().nutrients.keys
            exactKey = sampleKeys.find { it.lowercase() == normalizedQuery }
        }

        // Если точного нет, ищем ключ, содержащий подстроку
        val targetKey = if (exactKey != null) {
            exactKey
        } else {
            var foundKey: String? = null
            for (product in products) {
                foundKey = product.nutrients.keys.find { it.lowercase().contains(normalizedQuery) }
                if (foundKey != null) break
            }
            foundKey
        }

        if (targetKey == null) return emptyList()

        val result = mutableListOf<Pair<Product, Double>>()
        for (product in products) {
            val value = product.getNutrientValue(targetKey) ?: continue
            val calories = product.getCalories() ?: continue
            if (calories <= 0) continue
            result.add(product to (value / calories))
        }
        return result.sortedByDescending { it.second }.take(limit)
    }

    fun getAllNutrientNames(): List<String> {
        val set = mutableSetOf<String>()
        for (product in products) {
            set.addAll(product.nutrients.keys)
        }
        return set.sorted()
    }


}