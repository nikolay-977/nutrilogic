package com.example.nutrilogic.controller

import com.example.nutrilogic.model.Product
import com.example.nutrilogic.service.ProductService
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import kotlin.math.max

@Controller
class RecommendController(private val productService: ProductService) {

    // ---------- DTO ----------
    data class RequirementDto(
        val nutrientName: String,
        val dailyNorm: Double      // всегда в миллиграммах
    )

    data class RecommendationItem(
        val product: Product,
        val effectiveness: Double,
        val requiredGrams: Double?
    )

    internal data class Candidate(
        val product: Product,
        val effectiveness: Double,
        val nutrientValueMg: Double,
        val requiredGrams: Double,
        val caloriesPerServing: Double,
        val category: String
    )

    data class MealItem(
        val product: Product,
        val requiredGrams: Double,
        val nutrientName: String,
        val contributesNorm: Double
    )

    data class MealSetDto(
        val id: Int,
        val items: List<MealItem>,
        val totalCalories: Double
    ) {
        fun copy(id: Int) = MealSetDto(id, items, totalCalories)
    }

    // ---------- Конвертация единиц ----------
    private fun convertToMg(nutrientName: String, value: Double): Double {
        val macroKeywords = listOf("белки", "белка", "жиры", "жира", "углеводы", "углеводов")
        return if (macroKeywords.any { nutrientName.lowercase().contains(it) }) {
            value * 1000.0   // граммы → миллиграммы
        } else {
            value
        }
    }

    // ---------- Основные маршруты ----------
    @GetMapping("/")
    fun home(): String = "redirect:/recommend"

    @GetMapping("/recommend")
    fun recommend(
        @RequestParam(name = "nutrientName", required = false, defaultValue = "") nutrientName: String,
        @RequestParam(name = "category", required = false, defaultValue = "") category: String,
        @RequestParam(name = "norm", required = false, defaultValue = "0.0") norm: Double,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "10") size: Int,
        model: Model
    ): String {
        val nutrientList = productService.getAllNutrientNames()
        val categoryList = productService.getAllCategoryNames()
        model.addAttribute("nutrientList", nutrientList)
        model.addAttribute("categoryList", categoryList)
        model.addAttribute("selectedCategory", category)
        model.addAttribute("pageSize", size)
        // Сохраняем исходную норму (как ввёл пользователь) для отображения в форме
        model.addAttribute("norm", norm)

        if (nutrientName.isBlank()) {
            model.addAttribute("nutrientName", "")
            model.addAttribute("recommendations", emptyList<RecommendationItem>())
            model.addAttribute("currentPage", 0)
            model.addAttribute("totalPages", 0)
            model.addAttribute("totalItems", 0)
            return "index"
        }

        // Для расчётов переводим норму в мг (если это макронутриент)
        val normMg = if (norm > 0) convertToMg(nutrientName, norm) else 0.0

        val allRecommendationsRaw = productService.recommendProductsAll(
            nutrientName,
            if (category.isBlank()) null else category
        )

        val allRecommendations = allRecommendationsRaw.mapNotNull { (product, effectiveness) ->
            val requiredGrams = if (normMg > 0) {
                val nutrientValueMg = product.getNutrientValue(nutrientName)
                if (nutrientValueMg != null && nutrientValueMg > 0) {
                    (normMg / nutrientValueMg) * 100.0
                } else null
            } else null
            RecommendationItem(product, effectiveness, requiredGrams)
        }

        val totalItems = allRecommendations.size
        val totalPages = if (size > 0 && totalItems > 0) (totalItems + size - 1) / size else 1
        val start = page * size
        val end = minOf(start + size, totalItems)
        val pageItems = if (start < totalItems) allRecommendations.subList(start, end) else emptyList()

        model.addAttribute("nutrientName", nutrientName)
        model.addAttribute("recommendations", pageItems)
        model.addAttribute("currentPage", page)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("totalItems", totalItems)
        return "index"
    }

    @GetMapping("/search")
    fun searchProducts(
        @RequestParam(name = "name", required = false, defaultValue = "") name: String,
        @RequestParam(name = "category", required = false, defaultValue = "") category: String,
        model: Model
    ): String {
        val products = productService.searchProducts(name, if (category.isBlank()) null else category)
        val categoryList = productService.getAllCategoryNames()
        model.addAttribute("categoryList", categoryList)
        model.addAttribute("selectedCategory", category)
        model.addAttribute("searchName", name)
        model.addAttribute("products", products)
        return "search"
    }

    // ---------- Планировщик меню ----------
    @GetMapping("/meal-planner")
    fun mealPlannerPage(model: Model, session: HttpSession): String {
        model.addAttribute("nutrientList", productService.getAllNutrientNames())
        model.addAttribute("categoryList", productService.getAllCategoryNames())
        val requirements = session.getAttribute("requirements") as? MutableList<RequirementDto> ?: mutableListOf()
        model.addAttribute("requirements", requirements)
        return "meal-planner"
    }

    @PostMapping("/meal-planner/add-requirement")
    fun addRequirement(
        @RequestParam nutrientName: String,
        @RequestParam dailyNorm: Double,
        session: HttpSession
    ): String {
        if (nutrientName.isNotBlank() && dailyNorm > 0) {
            val requirements = session.getAttribute("requirements") as? MutableList<RequirementDto>
                ?: mutableListOf()
            // Конвертируем норму в миллиграммы если нужно
            val normMg = convertToMg(nutrientName, dailyNorm)
            requirements.add(RequirementDto(nutrientName, normMg))
            session.setAttribute("requirements", requirements)
        }
        return "redirect:/meal-planner"
    }

    @PostMapping("/meal-planner/clear-requirements")
    fun clearRequirements(session: HttpSession): String {
        session.removeAttribute("requirements")
        return "redirect:/meal-planner"
    }

    @GetMapping("/meal-planner/generate")
    fun generateMealSets(
        @RequestParam(defaultValue = "5") maxSets: Int,
        @RequestParam(defaultValue = "500") maxGramsPerProduct: Double,
        session: HttpSession,
        model: Model
    ): String {
        val requirements = session.getAttribute("requirements") as? List<RequirementDto>
            ?: emptyList()

        if (requirements.isEmpty()) {
            return "redirect:/meal-planner?error=no_requirements"
        }

        val setsCount = minOf(maxSets, 10)
        val mealSets = generateBalancedSets(requirements, setsCount, maxGramsPerProduct)

        model.addAttribute("mealSets", mealSets)
        model.addAttribute("requirements", requirements)
        model.addAttribute("nutrientList", productService.getAllNutrientNames())
        model.addAttribute("categoryList", productService.getAllCategoryNames())

        return "meal-planner"
    }

    // ---------- Логика генерации наборов ----------
// ---------- Логика генерации наборов (исправленная) ----------
    private fun generateBalancedSets(
        requirements: List<RequirementDto>,
        setsCount: Int,
        maxGramsPerProduct: Double
    ): List<MealSetDto> {
        if (requirements.isEmpty()) return emptyList()

        // ---- Случай одного нутриента: просто лучшие продукты без ограничения категорий ----
        if (requirements.size == 1) {
            val req = requirements[0]
            val candidates = productService.recommendProductsAll(req.nutrientName, null)
                .mapNotNull { (product, _) ->
                    val rawValue = product.getNutrientValue(req.nutrientName)
                    if (rawValue != null && rawValue > 0) {
                        val valueMg = normalizeNutrientValue(req.nutrientName, rawValue)
                        val requiredGrams = (req.dailyNorm / valueMg) * 100.0
                        if (requiredGrams <= maxGramsPerProduct) {
                            Candidate(
                                product = product,
                                effectiveness = valueMg / (product.getCalories() ?: 1.0),
                                nutrientValueMg = valueMg,
                                requiredGrams = requiredGrams,
                                caloriesPerServing = product.getCalories() ?: 0.0,
                                category = product.category.ifBlank { "Без категории" }
                            )
                        } else null
                    } else null
                }
                .distinctBy { it.product.name }
                .sortedByDescending { it.effectiveness }
                .take(setsCount)

            return candidates.mapIndexed { idx, cand ->
                val mealItem = MealItem(
                    product = cand.product,
                    requiredGrams = cand.requiredGrams,
                    nutrientName = req.nutrientName,
                    contributesNorm = req.dailyNorm
                )
                val totalCalories = cand.caloriesPerServing * (cand.requiredGrams / 100.0)
                MealSetDto(idx + 1, listOf(mealItem), totalCalories)
            }
        }

        // ---- Случай нескольких требований: последовательный подбор с вычитанием ----
        val allSets = mutableListOf<MealSetDto>()
        val usedProductsGlobal = mutableSetOf<Product>()
        val maxAttempts = 500

        for (attempt in 0 until maxAttempts) {
            if (allSets.size >= setsCount) break

            // Перемешиваем порядок требований для разнообразия
            val shuffledReqs = requirements.shuffled()
            val result = generateSequentialSet(shuffledReqs, maxGramsPerProduct, usedProductsGlobal)
            if (result != null) {
                val key = result.items.joinToString { it.product.name }
                if (allSets.none { it.items.joinToString { p -> p.product.name } == key }) {
                    allSets.add(result.copy(id = allSets.size + 1))
                    result.items.forEach { usedProductsGlobal.add(it.product) }
                }
            }
        }
        return allSets
    }

    /**
     * Генерирует один набор, последовательно покрывая требования с вычитанием.
     */
    private fun generateSequentialSet(
        requirements: List<RequirementDto>,
        maxGramsPerProduct: Double,
        usedProductsGlobal: MutableSet<Product>
    ): MealSetDto? {
        val remainingNorms = requirements.map { it.dailyNorm }.toDoubleArray()
        val selectedItems = mutableListOf<MealItem>()
        val usedProductsInSet = mutableSetOf<Product>()

        for ((idx, req) in requirements.withIndex()) {
            val nutrientName = req.nutrientName
            val remaining = remainingNorms[idx]
            if (remaining <= 0.0) continue

            val candidates = productService.recommendProductsAll(nutrientName, null)
                .mapNotNull { (product, _) ->
                    if (product in usedProductsGlobal || product in usedProductsInSet) return@mapNotNull null
                    val rawValue = product.getNutrientValue(nutrientName) ?: return@mapNotNull null
                    val valueMg = normalizeNutrientValue(nutrientName, rawValue)
                    if (valueMg <= 0) return@mapNotNull null
                    val requiredGrams = (remaining / valueMg) * 100.0
                    if (requiredGrams > maxGramsPerProduct) return@mapNotNull null
                    val effectiveness = valueMg / (product.getCalories() ?: 1.0)
                    Candidate(
                        product = product,
                        effectiveness = effectiveness,
                        nutrientValueMg = valueMg,
                        requiredGrams = requiredGrams,
                        caloriesPerServing = product.getCalories() ?: 0.0,
                        category = product.category
                    )
                }
                .sortedByDescending { it.effectiveness }

            val best = candidates.firstOrNull() ?: return null
            selectedItems.add(
                MealItem(
                    product = best.product,
                    requiredGrams = best.requiredGrams,
                    nutrientName = nutrientName,
                    contributesNorm = remaining
                )
            )
            usedProductsInSet.add(best.product)

            // Вычитаем вклад продукта из всех нутриентов
            for (j in requirements.indices) {
                if (remainingNorms[j] <= 0) continue
                val otherNutrient = requirements[j].nutrientName
                val otherRaw = best.product.getNutrientValue(otherNutrient) ?: continue
                val otherValueMg = normalizeNutrientValue(otherNutrient, otherRaw)
                if (otherValueMg > 0) {
                    val contributed = otherValueMg * (best.requiredGrams / 100.0)
                    remainingNorms[j] = max(0.0, remainingNorms[j] - contributed)
                }
            }
        }

        val totalCalories = selectedItems.sumOf {
            (it.product.getCalories() ?: 0.0) * (it.requiredGrams / 100.0)
        }
        return MealSetDto(0, selectedItems, totalCalories)
    }

    private fun normalizeNutrientValue(nutrientName: String, valueMg: Double): Double {
        val macroKeywords = listOf("белки", "белка", "жиры", "жира", "углеводы", "углеводов")
        // Если значение меньше 1000, возможно, это граммы, и нутриент — макро
        return if (macroKeywords.any { nutrientName.lowercase().contains(it) } && valueMg < 1000) {
            valueMg * 1000.0  // переводим граммы в миллиграммы
        } else {
            valueMg
        }
    }
}