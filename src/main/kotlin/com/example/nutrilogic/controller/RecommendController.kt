package com.example.nutrilogic.controller

import com.example.nutrilogic.entity.ProductEntity
import com.example.nutrilogic.entity.UserEntity
import com.example.nutrilogic.service.ProductService
import com.example.nutrilogic.service.UserService
import jakarta.servlet.http.HttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@Controller
class RecommendController(
    private val productService: ProductService,
    private val userService: UserService
) {

    data class RequirementDto(
        val nutrientName: String,
        val targetNorm: Double,
        val minNorm: Double = 0.0,
        val maxNorm: Double = 0.0,
        val unit: String? = null,
        val maxGramsPerProduct: Double = 500.0
    )

    data class RecommendationItem(
        val product: ProductEntity,
        val effectiveness: Double,
        val requiredGrams: Double?,
        val coveredPercent: Double?,
        val isCapped: Boolean = false,
        val caloriesPer100g: Double = 0.0,
        val nutrientValuePer100g: Double = 0.0
    )

    data class MealItem(
        val product: ProductEntity,
        val requiredGrams: Double,
        val nutrientName: String,
        val contributesNorm: Double,
        val unit: String?,
        val contributions: Map<String, Double> = emptyMap()
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

    private fun getCurrentUser(): UserEntity {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication.principal as OAuth2User
        val githubId = principal.getAttribute<Any>("id").toString().toString()
        return userService.getUserByGithubId(githubId)
            ?: throw IllegalStateException("User not found in DB")
    }

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
        val user = getCurrentUser()
        val bannedProductNames = user.bannedProducts.map { it.name }.toSet()

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
        model.addAttribute("favoriteProducts", user.favoriteProducts.map { it.name })

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

        val allRecommendationsRaw =
            productService.recommendProductsAll(nutrientName, if (category.isBlank()) null else category)
        val filteredRaw = allRecommendationsRaw.filter { (product, _) -> product.name !in bannedProductNames }

        val allRecommendations = filteredRaw.mapNotNull { (product, effectiveness) ->
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

    @PostMapping("/recommend/add-favorite")
    fun addFavorite(
        @RequestParam productName: String,
        @RequestHeader(value = "referer", required = false) referer: String?
    ): String {
        val user = getCurrentUser()
        userService.addFavoriteProduct(user, productName)
        return "redirect:${referer ?: "/recommend"}"
    }

    @PostMapping("/recommend/remove-favorite")
    fun removeFavorite(
        @RequestParam productName: String,
        @RequestHeader(value = "referer", required = false) referer: String?
    ): String {
        val user = getCurrentUser()
        userService.removeFavoriteProduct(user, productName)
        return "redirect:${referer ?: "/recommend"}"
    }

    @PostMapping("/recommend/add-banned")
    fun addBanned(
        @RequestParam productName: String,
        @RequestHeader(value = "referer", required = false) referer: String?
    ): String {
        val user = getCurrentUser()
        userService.addBannedProduct(user, productName)
        return "redirect:${referer ?: "/recommend"}"
    }

    @PostMapping("/recommend/remove-banned")
    fun removeBanned(
        @RequestParam productName: String,
        @RequestHeader(value = "referer", required = false) referer: String?
    ): String {
        val user = getCurrentUser()
        userService.removeBannedProduct(user, productName)
        return "redirect:${referer ?: "/recommend"}"
    }

    @GetMapping("/search")
    fun searchProducts(
        @RequestParam(name = "name", required = false, defaultValue = "") name: String,
        @RequestParam(name = "category", required = false, defaultValue = "") category: String,
        @RequestParam(name = "sort", required = false, defaultValue = "efficiency") sort: String,
        model: Model
    ): String {
        val user = getCurrentUser()
        val bannedProductNames = user.bannedProducts.map { it.name }.toSet()
        var allProducts = productService.searchProducts(name, if (category.isBlank()) null else category)
        allProducts = allProducts.filter { it.name !in bannedProductNames }.toMutableList()

        data class ProductWithEfficiency(val product: ProductEntity, val efficiency: Double)

        val productsWithEfficiency = allProducts.map { product ->
            val protein = product.getNutrientValue("Белки") ?: 0.0
            val calories = product.getCalories() ?: 1.0
            val efficiency = if (protein > 0 && calories > 0) protein / calories else 0.0
            ProductWithEfficiency(product, efficiency)
        }

        val sortedProducts = when (sort) {
            "name" -> productsWithEfficiency.sortedBy { it.product.name }
            else -> productsWithEfficiency.sortedByDescending { it.efficiency }
        }

        val categoryList = productService.getAllCategoryNames()
        model.addAttribute("categoryList", categoryList)
        model.addAttribute("selectedCategory", category)
        model.addAttribute("searchName", name)
        model.addAttribute("selectedSort", sort)
        model.addAttribute("productsWithEfficiency", sortedProducts)
        model.addAttribute("totalProducts", sortedProducts.size)
        model.addAttribute("favoriteProducts", user.favoriteProducts.map { it.name })
        return "search"
    }

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
        val user = getCurrentUser()
        val mealSets = generateBalancedSetsFast(requirements, setsCount, user)
        model.addAttribute("mealSets", mealSets)
        model.addAttribute("requirements", requirements)
        model.addAttribute("nutrientList", productService.getAllNutrientNames())
        model.addAttribute("categoryList", productService.getAllCategoryNames())
        return "meal-planner"
    }

    private fun generateBalancedSetsFast(
        requirements: List<RequirementDto>,
        setsCount: Int,
        user: UserEntity
    ): List<MealSetDto> {
        val bannedProductNames = user.bannedProducts.map { it.name }.toSet()
        var availableProducts = productService.searchProducts("", null)
            .filter { it.name !in bannedProductNames }
            .toMutableList()
        val resultSets = mutableListOf<MealSetDto>()

        for (setIndex in 0 until setsCount) {
            val setItems = mutableListOf<MealItem>()
            val usedProductsInSet = mutableSetOf<ProductEntity>()
            val usedCategoriesInSet = mutableSetOf<String>()
            var setValid = true

            for (req in requirements.reversed()) {
                var remaining = req.targetNorm
                val maxAllowed = if (req.maxNorm > 0) req.maxNorm else Double.MAX_VALUE
                var remainingSpace = maxAllowed
                if (remaining <= 0) continue

                var attempts = 0
                while (remaining > 0 && remainingSpace > 0 && attempts < 100) {
                    attempts++
                    val desiredIncrement = minOf(remaining, remainingSpace)
                    if (desiredIncrement <= 0) break

                    var best = availableProducts
                        .filter { it !in usedProductsInSet && it.category !in usedCategoriesInSet }
                        .mapNotNull { product ->
                            val valuePer100g = product.getNutrientValue(req.nutrientName) ?: return@mapNotNull null
                            if (valuePer100g <= 0.0) return@mapNotNull null
                            val effectiveness = valuePer100g / (product.getCalories() ?: 1.0)
                            Triple(product, valuePer100g, effectiveness)
                        }
                        .maxByOrNull { it.third }

                    if (best == null) {
                        best = availableProducts
                            .filter { it !in usedProductsInSet }
                            .mapNotNull { product ->
                                val valuePer100g = product.getNutrientValue(req.nutrientName) ?: return@mapNotNull null
                                if (valuePer100g <= 0.0) return@mapNotNull null
                                val effectiveness = valuePer100g / (product.getCalories() ?: 1.0)
                                Triple(product, valuePer100g, effectiveness)
                            }
                            .maxByOrNull { it.third }
                    }
                    if (best == null) {
                        setValid = false
                        break
                    }
                    val (product, valuePer100g) = best
                    val requiredGramsRaw = (desiredIncrement / valuePer100g) * 100.0
                    val actualGrams = minOf(requiredGramsRaw, req.maxGramsPerProduct)
                    var contribution = valuePer100g * (actualGrams / 100.0)
                    val contributionsMap = mutableMapOf<String, Double>()
                    for (r in requirements) {
                        val value = product.getNutrientValue(r.nutrientName) ?: 0.0
                        contributionsMap[r.nutrientName] = value * (actualGrams / 100.0)
                    }
                    if (contribution > desiredIncrement) contribution = desiredIncrement
                    if (contribution <= 0.0) continue

                    setItems.add(
                        MealItem(
                            product = product,
                            requiredGrams = actualGrams,
                            nutrientName = req.nutrientName,
                            contributesNorm = contribution,
                            unit = req.unit,
                            contributions = contributionsMap
                        )
                    )
                    usedProductsInSet.add(product)
                    usedCategoriesInSet.add(product.category)
                    remaining -= contribution
                    remainingSpace -= contribution
                }
                if (!setValid) break
            }

            if (setValid && setItems.isNotEmpty()) {
                val filteredItems = setItems.filter { it.contributesNorm > 0.0 }
                if (filteredItems.isEmpty()) break

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
                resultSets.add(MealSetDto(resultSets.size + 1, filteredItems, totalCalories, coverages))
                availableProducts.removeAll(usedProductsInSet)
            } else {
                break
            }
        }
        return resultSets
    }
}