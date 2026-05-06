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

    fun recommendProductsAll(nutrientQuery: String, category: String? = null): List<Pair<Product, Double>> {
        val normalizedQuery = nutrientQuery.trim().lowercase()
        if (products.isEmpty()) return emptyList()

        var targetKey: String? = null
        for (product in products) {
            targetKey = product.nutrients.keys.find { it.lowercase() == normalizedQuery || it.lowercase().contains(normalizedQuery) }
            if (targetKey != null) break
        }
        if (targetKey == null) return emptyList()

        val result = mutableListOf<Pair<Product, Double>>()
        for (product in products) {
            if (category != null && category.isNotBlank() && product.category != category) continue
            val value = product.getNutrientValue(targetKey) ?: continue
            val calories = product.getCalories() ?: continue
            if (calories <= 0) continue
            result.add(product to (value / calories))
        }
        return result.sortedByDescending { it.second }
    }

    fun getAllCategoryNames(): List<String> = products.map { it.category }.distinct().sorted()

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

    fun getAllNutrientNames(): List<String> = products.flatMap { it.nutrients.keys }.distinct().sorted()

    fun getUnitForNutrient(nutrientName: String): String? {
        val normalized = nutrientName.trim().lowercase()
        for (product in products) {
            val entry = product.nutrients.entries.find { it.key.lowercase() == normalized || it.key.lowercase().contains(normalized) }
            if (entry != null && entry.value.unit.isNotBlank()) return entry.value.unit
        }
        return null
    }

    // Для предпочтений: получение продукта по имени
    fun findProductByName(name: String): Product? = products.find { it.name == name }
}