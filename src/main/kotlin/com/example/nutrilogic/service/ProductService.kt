package com.example.nutrilogic.service

import com.example.nutrilogic.entity.ProductEntity
import com.example.nutrilogic.repository.ProductRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    fun uploadProductFromJson(file: MultipartFile): String {
        val mapper = jacksonObjectMapper()
        val jsonNode = mapper.readTree(file.inputStream)
        val name = jsonNode.get("name")?.asText() ?: return "Отсутствует поле 'name'"

        // Проверяем, существует ли продукт
        val existing = productRepository.findByName(name)
        if (existing != null) {
            return "Продукт '$name' уже существует"
        }

        val url = jsonNode.get("url")?.asText() ?: ""
        val category = jsonNode.get("category")?.asText() ?: ""

        val nutrientsMap = mutableMapOf<String, ProductEntity.NutrientValue>()
        val nutrientsNode = jsonNode.get("nutrients")
        nutrientsNode?.fields()?.asSequence()?.forEach { (key, valueNode) ->
            val value = valueNode.get("value")?.asText() ?: ""
            val unit = valueNode.get("unit")?.asText() ?: ""
            nutrientsMap[key] = ProductEntity.NutrientValue(value, unit)
        }

        val product = ProductEntity(
            name = name,
            url = url,
            category = category,
            nutrients = nutrientsMap
        )
        productRepository.save(product)
        return "Продукт '$name' успешно загружен"
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

    fun getAllNutrientNames(): List<String> =
        productRepository.findAll().flatMap { it.nutrients.keys }.distinct().sorted()

    fun getUnitForNutrient(nutrientName: String): String? {
        val normalized = nutrientName.trim().lowercase()
        val allProducts = productRepository.findAll()
        for (product in allProducts) {
            val entry = product.nutrients.entries.find {
                it.key.lowercase() == normalized || it.key.lowercase().contains(normalized)
            }
            if (entry != null && entry.value.unit.isNotBlank()) return entry.value.unit
        }
        return null
    }
}