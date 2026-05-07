package com.example.nutrilogic.controller

import com.example.nutrilogic.entity.UserEntity
import com.example.nutrilogic.service.DiaryService
import com.example.nutrilogic.service.ProductService
import com.example.nutrilogic.service.UserService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Controller
class UserController(
    private val userService: UserService,
    private val diaryService: DiaryService,
    private val productService: ProductService   // ← добавлена зависимость
) {

    data class NutrientProgress(
        val name: String,
        val target: Double,
        val consumed: Double,
        val unit: String,
        val isCustom: Boolean,
        val minNorm: Double,
        val maxNorm: Double,
        var progressPercent: Double = 0.0,
        var colorClass: String = "bg-info",
        var statusText: String = ""
    )

    private fun getCurrentUser(): UserEntity {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication.principal as OAuth2User
        val githubId = principal.getAttribute<Any>("id").toString().toString()
        return userService.getUserByGithubId(githubId)
            ?: throw IllegalStateException("User not found in DB")
    }

    private fun parseDate(dateStr: String?): LocalDate {
        return if (dateStr != null && dateStr.isNotBlank()) LocalDate.parse(dateStr) else LocalDate.now()
    }

    @GetMapping("/profile")
    fun profilePage(model: Model): String {
        val user = getCurrentUser()
        model.addAttribute("profile", user)
        return "profile"
    }

    @PostMapping("/profile/save")
    fun saveProfile(
        @RequestParam name: String,
        @RequestParam gender: String,
        @RequestParam birthDate: String,
        @RequestParam height: Double,
        @RequestParam weight: Double,
        @RequestParam activityLevel: String,
        @RequestParam targetCalories: Double,
        @RequestParam targetProtein: Double,
        @RequestParam targetFat: Double,
        @RequestParam targetCarbs: Double
    ): String {
        val user = getCurrentUser()
        user.name = name
        user.gender = gender
        user.birthDate = if (birthDate.isNotBlank()) LocalDate.parse(birthDate) else null
        user.height = height
        user.weight = weight
        user.activityLevel = activityLevel
        user.targetCalories = targetCalories
        user.targetProtein = targetProtein
        user.targetFat = targetFat
        user.targetCarbs = targetCarbs
        userService.updateUser(user)
        return "redirect:/profile"
    }

    @GetMapping("/dashboard")
    fun dashboard(model: Model): String {
        val user = getCurrentUser()
        val today = LocalDate.now()
        val diaryEntry = diaryService.getDiaryEntry(user, today)

        // Суммируем нутриенты за день
        val totalCalories = diaryEntry.consumedProducts.sumOf { it.calories }
        val totalProtein = diaryEntry.consumedProducts.sumOf { it.nutrients["Белки"] ?: 0.0 }
        val totalFat = diaryEntry.consumedProducts.sumOf { it.nutrients["Жиры"] ?: 0.0 }
        val totalCarbs = diaryEntry.consumedProducts.sumOf { it.nutrients["Углеводы"] ?: 0.0 }

        val totals = mapOf(
            "calories" to totalCalories,
            "protein" to totalProtein,
            "fat" to totalFat,
            "carbs" to totalCarbs
        )

        val allNutrients = mutableListOf<NutrientProgress>()
        allNutrients.add(NutrientProgress("Калории", user.targetCalories, totals["calories"] ?: 0.0, "ккал", isCustom = false, minNorm = 0.0, maxNorm = 0.0))
        allNutrients.add(NutrientProgress("Белки", user.targetProtein, totals["protein"] ?: 0.0, "г", isCustom = false, minNorm = 0.0, maxNorm = 0.0))
        allNutrients.add(NutrientProgress("Жиры", user.targetFat, totals["fat"] ?: 0.0, "г", isCustom = false, minNorm = 0.0, maxNorm = 0.0))
        allNutrients.add(NutrientProgress("Углеводы", user.targetCarbs, totals["carbs"] ?: 0.0, "г", isCustom = false, minNorm = 0.0, maxNorm = 0.0))

        // Произвольные цели пользователя
        val customTargets = userService.getCustomTargets(user)
        customTargets.forEach { (name, norms) ->
            val target = norms.first
            val minNorm = norms.second
            val maxNorm = norms.third
            val consumed = diaryEntry.consumedProducts.sumOf { it.nutrients[name] ?: 0.0 }
            allNutrients.add(NutrientProgress(name, target, consumed, "мг", isCustom = true, minNorm = minNorm, maxNorm = maxNorm))
        }

        val nutrientsWithStatus = allNutrients.map { nutrient ->
            val progressPercent = if (nutrient.target > 0) (nutrient.consumed / nutrient.target * 100).coerceIn(0.0, 100.0) else 0.0
            val (colorClass, statusText) = when {
                nutrient.minNorm > 0 && nutrient.consumed < nutrient.minNorm -> "bg-warning" to "⚠️ Ниже минимума"
                nutrient.consumed < nutrient.target -> "bg-info" to "⚡ Ниже цели"
                nutrient.maxNorm > 0 && nutrient.consumed > nutrient.maxNorm -> "bg-danger" to "🔴 Превышение"
                else -> "bg-success" to "✅ Достигнуто"
            }
            nutrient.copy(progressPercent = progressPercent, colorClass = colorClass, statusText = statusText)
        }

        model.addAttribute("profile", user)
        model.addAttribute("nutrients", nutrientsWithStatus)
        return "dashboard"
    }

    @GetMapping("/diary")
    fun diaryPage(
        @RequestParam(value = "date", required = false) dateStr: String?,
        model: Model
    ): String {
        val user = getCurrentUser()
        val date = parseDate(dateStr)
        val diaryEntry = diaryService.getDiaryEntry(user, date)
        val meals = diaryEntry.consumedProducts.groupBy { it.mealType }

        // Все продукты (без учёта бан-листа — фильтруем на уровне сервиса или тут)
        val allProducts = productService.searchProducts("", null) // нужно внедрить ProductService
        val bannedProductNames = user.bannedProducts.map { it.name }.toSet()
        val filteredProducts = allProducts.filter { it.name !in bannedProductNames }
        val favoriteProductNames = user.favoriteProducts.map { it.name }.toSet()
        val (favProducts, otherProducts) = filteredProducts.partition { it.name in favoriteProductNames }
        val sortedProducts = favProducts + otherProducts

        model.addAttribute("date", date)
        model.addAttribute("meals", meals)  // нужно преобразовать в DTO или использовать готовую модель
        model.addAttribute("profile", user)
        model.addAttribute("allProducts", sortedProducts)
        return "diary"
    }

    @PostMapping("/diary/add")
    fun addProductToDiary(
        @RequestParam productId: String,
        @RequestParam quantity: Double,
        @RequestParam mealType: String,
        @RequestParam date: String
    ): String {
        val user = getCurrentUser()
        val entryDate = parseDate(date)
        diaryService.addConsumedProduct(user, entryDate, productId, quantity, mealType)
        return "redirect:/diary?date=$date"
    }

    @PostMapping("/diary/remove")
    fun removeProductFromDiary(
        @RequestParam date: String,
        @RequestParam mealType: String,
        @RequestParam index: Int
    ): String {
        val user = getCurrentUser()
        val entryDate = parseDate(date)
        val diaryEntry = diaryService.getDiaryEntry(user, entryDate)
        // В consumedProducts порядок соответствует списку; получаем конкретный consumed продукт
        val consumedList = diaryEntry.consumedProducts.filter { it.mealType == mealType }
        if (index in consumedList.indices) {
            diaryService.removeConsumedProduct(consumedList[index].id)
        }
        return "redirect:/diary?date=$date"
    }

    @PostMapping("/diary/clear-day")
    fun clearDay(@RequestParam date: String): String {
        val user = getCurrentUser()
        val entryDate = parseDate(date)
        diaryService.clearDiaryDay(user, entryDate)
        return "redirect:/diary?date=$date"
    }

    @GetMapping("/profile/preferences")
    fun preferencesPage(model: Model): String {
        val user = getCurrentUser()
        model.addAttribute("favoriteProducts", user.favoriteProducts.map { it.name })
        model.addAttribute("bannedProducts", user.bannedProducts.map { it.name })
        return "preferences"
    }

    @PostMapping("/profile/remove-favorite")
    fun removeFavorite(@RequestParam productName: String): String {
        val user = getCurrentUser()
        userService.removeFavoriteProduct(user, productName)
        return "redirect:/profile/preferences"
    }

    @PostMapping("/profile/remove-banned")
    fun removeBanned(@RequestParam productName: String): String {
        val user = getCurrentUser()
        userService.removeBannedProduct(user, productName)
        return "redirect:/profile/preferences"
    }

    @PostMapping("/dashboard/add-custom-target")
    fun addCustomTargetOnDashboard(
        @RequestParam nutrientName: String,
        @RequestParam targetNorm: Double,
        @RequestParam minNorm: Double,
        @RequestParam maxNorm: Double
    ): String {
        if (nutrientName.isNotBlank() && targetNorm > 0) {
            val user = getCurrentUser()
            userService.addCustomTarget(user, nutrientName, targetNorm, minNorm, maxNorm)
        }
        return "redirect:/dashboard"
    }

    @PostMapping("/dashboard/remove-custom-target")
    fun removeCustomTargetFromDashboard(@RequestParam nutrientName: String): String {
        val user = getCurrentUser()
        userService.removeCustomTarget(user, nutrientName)
        return "redirect:/dashboard"
    }
}