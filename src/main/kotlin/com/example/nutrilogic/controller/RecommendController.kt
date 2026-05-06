package com.example.nutrilogic.controller

import com.example.nutrilogic.model.Product
import com.example.nutrilogic.service.ProductService
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import kotlin.math.max
import kotlin.math.min

@Controller
class RecommendController(private val productService: ProductService) {

    // ---------- DTOs ----------
    data class RequirementDto(
        val nutrientName: String,
        val targetNorm: Double,
        val minNorm: Double = 0.0,
        val maxNorm: Double = 0.0,
        val unit: String? = null,
        val maxGramsPerProduct: Double = 500.0
    )

    data class RecommendationItem(
        val product: Product,
        val effectiveness: Double,
        val requiredGrams: Double?,
        val coveredPercent: Double?,
        val isCapped: Boolean = false,
        val caloriesPer100g: Double = 0.0,
        val nutrientValuePer100g: Double = 0.0
    )

    internal data class Candidate(
        val product: Product,
        val effectiveness: Double,
        val nutrientValue: Double,
        val requiredGrams: Double,
        val caloriesPerServing: Double,
        val category: String
    )

    data class MealItem(
        val product: Product,
        val requiredGrams: Double,
        val nutrientName: String,
        val contributesNorm: Double,
        val unit: String?
    )

    data class RequirementCoverage(
        val nutrientName: String,
        val totalCovered: Double,
        val targetNorm: Double,
        val minNorm: Double,
        val maxNorm: Double,
        val unit: String?
    )

    data class MealSetDto(
        val id: Int,
        val items: List<MealItem>,
        val totalCalories: Double,
        val coverages: List<RequirementCoverage>
    ) {
        fun copy(id: Int) = MealSetDto(id, items, totalCalories, coverages)
    }

    // ---------- Основные маршруты ----------
    @GetMapping("/")
    fun home(): String = "redirect:/recommend"

    @GetMapping("/recommend")
    fun recommend(
        @RequestParam(name = "nutrientName", required = false, defaultValue = "") nutrientName: String,
        @RequestParam(name = "category", required = false, defaultValue = "") category: String,
        @RequestParam(name = "norm", required = false, defaultValue = "0.0") norm: Double,
        @RequestParam(name = "minNorm", required = false, defaultValue = "0.0") minNorm: Double,
        @RequestParam(name = "maxNorm", required = false, defaultValue = "0.0") maxNorm: Double,
        @RequestParam(name = "maxGramsPerProduct", required = false, defaultValue = "0.0") maxGramsPerProduct: Double,
        @RequestParam(name = "sort", required = false, defaultValue = "efficiency") sort: String,
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
        model.addAttribute("norm", norm)
        model.addAttribute("minNorm", minNorm)
        model.addAttribute("maxNorm", maxNorm)
        model.addAttribute("maxGramsPerProduct", maxGramsPerProduct)
        model.addAttribute("selectedSort", sort)

        var unit: String? = null
        if (nutrientName.isNotBlank()) {
            unit = productService.getUnitForNutrient(nutrientName) ?: "ед."
        }
        model.addAttribute("unit", unit)

        if (nutrientName.isBlank()) {
            model.addAttribute("nutrientName", "")
            model.addAttribute("recommendations", emptyList<RecommendationItem>())
            model.addAttribute("currentPage", 0)
            model.addAttribute("totalPages", 0)
            model.addAttribute("totalItems", 0)
            return "index"
        }

        val allRecommendationsRaw = productService.recommendProductsAll(nutrientName, if (category.isBlank()) null else category)
        val allRecommendations = allRecommendationsRaw.mapNotNull { (product, effectiveness) ->
            val nutrientValue = product.getNutrientValue(nutrientName) ?: return@mapNotNull null
            if (nutrientValue <= 0.0) return@mapNotNull null

            var requiredGrams = if (norm > 0) (norm / nutrientValue) * 100.0 else null
            var isCapped = false

            if (requiredGrams != null) {
                if (maxNorm > 0.0) {
                    val actualCovered = nutrientValue * (requiredGrams / 100.0)
                    if (actualCovered > maxNorm) {
                        requiredGrams = (maxNorm / nutrientValue) * 100.0
                        isCapped = true
                    }
                }
                if (maxGramsPerProduct > 0.0 && requiredGrams > maxGramsPerProduct) {
                    requiredGrams = maxGramsPerProduct
                    isCapped = true
                }
            }

            val coveredPercent = if (requiredGrams != null && norm > 0) {
                (nutrientValue * (requiredGrams / 100.0)) / norm * 100
            } else null

            val calories = product.getCalories() ?: 0.0
            RecommendationItem(
                product = product,
                effectiveness = effectiveness,
                requiredGrams = requiredGrams,
                coveredPercent = coveredPercent,
                isCapped = isCapped,
                caloriesPer100g = calories,
                nutrientValuePer100g = nutrientValue
            )
        }

        // Сортировка
        val sortedRecommendations = when (sort) {
            "coverage" -> allRecommendations.sortedByDescending { it.coveredPercent ?: 0.0 }
            else -> allRecommendations.sortedByDescending { it.effectiveness }
        }

        val totalItems = sortedRecommendations.size
        val totalPages = if (size > 0 && totalItems > 0) (totalItems + size - 1) / size else 1
        val start = page * size
        val end = minOf(start + size, totalItems)
        val pageItems = if (start < totalItems) sortedRecommendations.subList(start, end) else emptyList()

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
        @RequestParam targetNorm: Double,
        @RequestParam(defaultValue = "0.0") minNorm: Double,
        @RequestParam(defaultValue = "0.0") maxNorm: Double,
        @RequestParam(defaultValue = "500.0") maxGramsPerProduct: Double,
        session: HttpSession
    ): String {
        if (nutrientName.isNotBlank() && targetNorm > 0) {
            val requirements = session.getAttribute("requirements") as? MutableList<RequirementDto>
                ?: mutableListOf()
            val unit = productService.getUnitForNutrient(nutrientName) ?: "ед."
            requirements.add(RequirementDto(nutrientName, targetNorm, minNorm, maxNorm, unit, maxGramsPerProduct))
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
        session: HttpSession,
        model: Model
    ): String {
        val requirements = session.getAttribute("requirements") as? List<RequirementDto> ?: emptyList()
        if (requirements.isEmpty()) {
            return "redirect:/meal-planner?error=no_requirements"
        }
        val setsCount = minOf(maxSets, 10)
        val mealSets = generateBalancedSets(requirements, setsCount)
        model.addAttribute("mealSets", mealSets)
        model.addAttribute("requirements", requirements)
        model.addAttribute("nutrientList", productService.getAllNutrientNames())
        model.addAttribute("categoryList", productService.getAllCategoryNames())
        return "meal-planner"
    }

    // ---------- Логика генерации наборов ----------
    private fun generateBalancedSets(requirements: List<RequirementDto>, setsCount: Int): List<MealSetDto> {
        if (requirements.isEmpty()) return emptyList()
        val candidates = mutableListOf<MealSetDto>()
        val maxAttempts = 800

        for (attempt in 0 until maxAttempts) {
            val shuffledReqs = requirements.shuffled()
            val set = generateSequentialSet(shuffledReqs, mutableSetOf())
            if (set != null) {
                val key = set.items.map { it.product.name }.sorted().joinToString()
                if (candidates.none { it.items.map { p -> p.product.name }.sorted().joinToString() == key }) {
                    if (set.coverages.all { cov -> cov.totalCovered >= cov.targetNorm * 0.1 }) {
                        candidates.add(set)
                    }
                }
            }
            if (candidates.size >= setsCount * 5) break
        }

        if (candidates.isEmpty()) return emptyList()

        fun score(set: MealSetDto): Double {
            var penalty = 0.0
            for (cov in set.coverages) {
                if (cov.minNorm > 0 && cov.totalCovered < cov.minNorm) {
                    penalty += (cov.minNorm - cov.totalCovered) * 2.0
                }
                if (cov.maxNorm > 0 && cov.totalCovered > cov.maxNorm) {
                    penalty += (cov.totalCovered - cov.maxNorm) * 1.5
                }
                penalty += kotlin.math.abs(cov.totalCovered - cov.targetNorm) * 0.1
            }
            return penalty
        }

        val bestCandidates = candidates.sortedBy { score(it) }.take(setsCount)
        return bestCandidates.mapIndexed { idx, set -> set.copy(id = idx + 1) }
    }

    private fun generateSequentialSet(
        requirements: List<RequirementDto>,
        usedProductsGlobal: MutableSet<Product>
    ): MealSetDto? {
        val remainingNorms = requirements.map { it.targetNorm }.toDoubleArray()
        val selectedItems = mutableListOf<MealItem>()
        val usedProductsInSet = mutableSetOf<Product>()

        for ((idx, req) in requirements.withIndex()) {
            var remaining = remainingNorms[idx]
            if (remaining <= 0.0) continue

            val itemsForReq = mutableListOf<MealItem>()
            while (remaining > 0.0) {
                val candidates = productService.recommendProductsAll(req.nutrientName, null)
                    .mapNotNull { (product, _) ->
                        if (product in usedProductsGlobal || product in usedProductsInSet) return@mapNotNull null
                        val valuePer100g = product.getNutrientValue(req.nutrientName) ?: return@mapNotNull null
                        if (valuePer100g <= 0.0) return@mapNotNull null
                        val requiredGramsRaw = (remaining / valuePer100g) * 100.0
                        val actualGrams = minOf(requiredGramsRaw, req.maxGramsPerProduct)
                        if (actualGrams <= 0.0) return@mapNotNull null
                        val contribution = valuePer100g * (actualGrams / 100.0)
                        if (contribution < 0.01) return@mapNotNull null
                        val effectiveness = valuePer100g / (product.getCalories() ?: 1.0)
                        Candidate(
                            product = product,
                            effectiveness = effectiveness,
                            nutrientValue = valuePer100g,
                            requiredGrams = actualGrams,
                            caloriesPerServing = product.getCalories() ?: 0.0,
                            category = product.category
                        )
                    }
                    .distinctBy { it.product.name }
                    .sortedByDescending { it.effectiveness }

                val best = candidates.firstOrNull() ?: break
                itemsForReq.add(
                    MealItem(
                        product = best.product,
                        requiredGrams = best.requiredGrams,
                        nutrientName = req.nutrientName,
                        contributesNorm = best.nutrientValue * (best.requiredGrams / 100.0),
                        unit = req.unit
                    )
                )
                usedProductsInSet.add(best.product)
                remaining -= (best.nutrientValue * (best.requiredGrams / 100.0))
            }
            if (itemsForReq.isEmpty() && remaining > 0.0) return null
            selectedItems.addAll(itemsForReq)

            for (item in itemsForReq) {
                for (j in requirements.indices) {
                    if (remainingNorms[j] <= 0.0) continue
                    val otherNutrient = requirements[j].nutrientName
                    val otherValue = item.product.getNutrientValue(otherNutrient) ?: continue
                    if (otherValue > 0.0) {
                        val contributed = otherValue * (item.requiredGrams / 100.0)
                        remainingNorms[j] = max(0.0, remainingNorms[j] - contributed)
                    }
                }
            }
        }

        val filteredItems = selectedItems.filter { it.contributesNorm >= 0.01 && it.requiredGrams >= 0.1 }

        val coverages = requirements.map { req ->
            val totalCovered = filteredItems.sumOf { item ->
                val valuePer100g = item.product.getNutrientValue(req.nutrientName) ?: 0.0
                valuePer100g * (item.requiredGrams / 100.0)
            }
            RequirementCoverage(
                nutrientName = req.nutrientName,
                totalCovered = totalCovered,
                targetNorm = req.targetNorm,
                minNorm = req.minNorm,
                maxNorm = req.maxNorm,
                unit = req.unit
            )
        }

        val totalCalories = filteredItems.sumOf {
            (it.product.getCalories() ?: 0.0) * (it.requiredGrams / 100.0)
        }

        return if (filteredItems.isNotEmpty()) MealSetDto(0, filteredItems, totalCalories, coverages) else null
    }
}