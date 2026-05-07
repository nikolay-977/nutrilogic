package com.example.nutrilogic.service

import com.example.nutrilogic.entity.ProductEntity
import com.example.nutrilogic.repository.ProductRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.io.File

@Service
class ProductService(
    private val productRepository: ProductRepository
) {

    @EventListener(ApplicationReadyEvent::class)
    fun loadProductsFromJson() {
        val dataDir = File("data/full_products")
        if (!dataDir.exists()) {
            println("⚠️ Папка data/full_products не найдена")
            return
        }
        val mapper = jacksonObjectMapper()
        var loadedCount = 0
        dataDir.listFiles { file -> file.extension == "json" }?.forEach { jsonFile ->
            try {
                val jsonNode = mapper.readTree(jsonFile)
                val name = jsonNode.get("name").asText()
                // Проверяем, существует ли уже продукт с таким именем
                if (productRepository.findByName(name) != null) {
                    return@forEach // пропускаем
                }
                val url = jsonNode.get("url").asText()
                val category = jsonNode.get("category")?.asText() ?: ""
                val nutrientsMap = mutableMapOf<String, ProductEntity.NutrientValue>()
                val nutrientsNode = jsonNode.get("nutrients")
                nutrientsNode.fields().asSequence().forEach { (key, valueNode) ->
                    val value = valueNode.get("value").asText()
                    val unit = valueNode.get("unit").asText()
                    nutrientsMap[key] = ProductEntity.NutrientValue(value, unit)
                }
                val product = ProductEntity(
                    name = name,
                    url = url,
                    category = category,
                    nutrients = nutrientsMap
                )
                productRepository.save(product)
                loadedCount++
            } catch (e: Exception) {
                println("Ошибка чтения ${jsonFile.name}: ${e.message}")
            }
        }
        println("✅ Загружено $loadedCount новых продуктов (всего ${productRepository.count()})")
    }

    fun recommendProductsAll(nutrientQuery: String, category: String? = null): List<Pair<ProductEntity, Double>> {
        val normalizedQuery = nutrientQuery.trim().lowercase()
        // Находим ключ нутриента
        val allProducts = productRepository.findAll()
        val targetKey = allProducts.asSequence()
            .flatMap { it.nutrients.keys }
            .firstOrNull { key -> key.lowercase() == normalizedQuery || key.lowercase().contains(normalizedQuery) }
            ?: return emptyList()

        return allProducts.asSequence()
            .filter { category == null || it.category == category }
            .mapNotNull { product ->
                val value = product.getNutrientValue(targetKey) ?: return@mapNotNull null
                val calories = product.getCalories() ?: return@mapNotNull null
                if (calories <= 0) return@mapNotNull null
                product to (value / calories)
            }
            .sortedByDescending { it.second }
            .toList()
    }

    fun getAllCategoryNames(): List<String> = productRepository.findAll().map { it.category }.distinct().sorted()

    fun searchProducts(nameQuery: String?, category: String?): MutableList<ProductEntity> {
        var result = productRepository.findAll().toMutableList()
        if (!nameQuery.isNullOrBlank()) {
            val lowerQuery = nameQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(lowerQuery) }.toMutableList()
        }
        if (!category.isNullOrBlank()) {
            result = result.filter { it.category == category }.toMutableList()
        }
        return result.sortedBy { it.name }.toMutableList()
    }

    fun getAllNutrientNames(): List<String> = productRepository.findAll().flatMap { it.nutrients.keys }.distinct().sorted()

    fun getUnitForNutrient(nutrientName: String): String? {
        val normalized = nutrientName.trim().lowercase()
        val allProducts = productRepository.findAll()
        for (product in allProducts) {
            val entry = product.nutrients.entries.find { it.key.lowercase() == normalized || it.key.lowercase().contains(normalized) }
            if (entry != null && entry.value.unit.isNotBlank()) return entry.value.unit
        }
        return null
    }
}