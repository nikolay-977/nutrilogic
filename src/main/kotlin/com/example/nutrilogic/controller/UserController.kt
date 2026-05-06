package com.example.nutrilogic.controller

import com.example.nutrilogic.model.*
import com.example.nutrilogic.service.ProductService
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Controller
class UserController(private val productService: ProductService) {

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

    private fun parseDate(dateStr: String?): LocalDate {
        return if (dateStr != null && dateStr.isNotBlank()) LocalDate.parse(dateStr) else LocalDate.now()
    }

    private fun getProfile(session: HttpSession): UserProfile {
        var profile = session.getAttribute("userProfile") as? UserProfile
        if (profile == null) {
            profile = UserProfile().apply {
                name = "Костин Николай Александрович"
                gender = "male"
                birthDate = LocalDate.parse("1988-08-27")
                height = 181.0
                weight = 82.0
                activityLevel = "light"
                targetCalories = 2000.0
                targetProtein = 120.0
                targetFat = 70.0
                targetCarbs = 250.0
                customTargets = mutableMapOf()
                favoriteProducts = mutableSetOf()
                bannedProducts = mutableSetOf()
            }
            session.setAttribute("userProfile", profile)
        }
        return profile
    }

    @GetMapping("/profile")
    fun profilePage(model: Model, session: HttpSession): String {
        model.addAttribute("profile", getProfile(session))
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
        @RequestParam targetCarbs: Double,
        session: HttpSession
    ): String {
        val birth = if (birthDate.isNotBlank()) LocalDate.parse(birthDate) else null
        val profile = getProfile(session)
        profile.name = name
        profile.gender = gender
        profile.birthDate = birth
        profile.height = height
        profile.weight = weight
        profile.activityLevel = activityLevel
        profile.targetCalories = targetCalories
        profile.targetProtein = targetProtein
        profile.targetFat = targetFat
        profile.targetCarbs = targetCarbs
        session.setAttribute("userProfile", profile)
        return "redirect:/dashboard"
    }

    @GetMapping("/dashboard")
    fun dashboard(model: Model, session: HttpSession): String {
        val profile = getProfile(session)
        val today = LocalDate.now()
        val diary = session.getAttribute("diary") as? MutableMap<LocalDate, DiaryEntry> ?: mutableMapOf()
        val todayEntry = diary[today] ?: DiaryEntry(today)

        val totals = mapOf(
            "calories" to todayEntry.totalCalories(),
            "protein" to todayEntry.totalNutrient("Белки"),
            "fat" to todayEntry.totalNutrient("Жиры"),
            "carbs" to todayEntry.totalNutrient("Углеводы")
        )

        val allNutrients = mutableListOf<NutrientProgress>()

        // Основные нутриенты
        allNutrients.add(NutrientProgress("Калории", profile.targetCalories, totals["calories"] ?: 0.0, "ккал", isCustom = false, minNorm = 0.0, maxNorm = 0.0))
        allNutrients.add(NutrientProgress("Белки", profile.targetProtein, totals["protein"] ?: 0.0, "г", isCustom = false, minNorm = 0.0, maxNorm = 0.0))
        allNutrients.add(NutrientProgress("Жиры", profile.targetFat, totals["fat"] ?: 0.0, "г", isCustom = false, minNorm = 0.0, maxNorm = 0.0))
        allNutrients.add(NutrientProgress("Углеводы", profile.targetCarbs, totals["carbs"] ?: 0.0, "г", isCustom = false, minNorm = 0.0, maxNorm = 0.0))

        // Произвольные нутриенты
        profile.customTargets.forEach { (name, norms) ->
            val target = norms.first
            val minNorm = norms.second
            val maxNorm = norms.third
            val consumed = todayEntry.totalNutrient(name)
            allNutrients.add(NutrientProgress(name, target, consumed, "мг", isCustom = true, minNorm = minNorm, maxNorm = maxNorm))
        }

        // Вычисляем класс цвета и статус для каждого
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

        model.addAttribute("profile", profile)
        model.addAttribute("nutrients", nutrientsWithStatus)
        return "dashboard"
    }

    @GetMapping("/diary")
    fun diaryPage(
        @RequestParam(value = "date", required = false) dateStr: String?,
        model: Model,
        session: HttpSession
    ): String {
        val profile = getProfile(session)
        val date = parseDate(dateStr)
        val diary = session.getAttribute("diary") as? MutableMap<LocalDate, DiaryEntry> ?: mutableMapOf()
        val entry = diary[date] ?: DiaryEntry(date)

        val allProducts = productService.searchProducts("", null)
        val filteredProducts = allProducts.filter { !profile.bannedProducts.contains(it.name) }
        val (favProducts, otherProducts) = filteredProducts.partition { profile.favoriteProducts.contains(it.name) }
        val sortedProducts = favProducts + otherProducts

        model.addAttribute("date", date)
        model.addAttribute("entry", entry)
        model.addAttribute("profile", profile)
        model.addAttribute("allProducts", sortedProducts)
        return "diary"
    }

    @PostMapping("/diary/add")
    fun addProductToDiary(
        @RequestParam productId: String,
        @RequestParam quantity: Double,
        @RequestParam mealType: String,
        @RequestParam date: String,
        session: HttpSession
    ): String {
        val profile = getProfile(session)
        val entryDate = parseDate(date)
        val product = productService.searchProducts(productId, null).firstOrNull()
            ?: return "redirect:/diary?date=$date&error=notfound"

        val factor = quantity / 100.0
        val consumed = ConsumedProduct(
            product = product,
            quantity = quantity,
            mealType = mealType,
            calories = (product.getCalories() ?: 0.0) * factor,
            nutrients = mapOf(
                "Белки" to (product.getNutrientValue("Белки") ?: 0.0) * factor,
                "Жиры" to (product.getNutrientValue("Жиры") ?: 0.0) * factor,
                "Углеводы" to (product.getNutrientValue("Углеводы") ?: 0.0) * factor
            )
        )

        val diary = session.getAttribute("diary") as? MutableMap<LocalDate, DiaryEntry> ?: mutableMapOf()
        val entry = diary[entryDate] ?: DiaryEntry(entryDate)
        entry.meals.getOrPut(mealType) { mutableListOf() }.add(consumed)
        diary[entryDate] = entry
        session.setAttribute("diary", diary)
        return "redirect:/diary?date=$date"
    }

    @PostMapping("/diary/remove")
    fun removeProductFromDiary(
        @RequestParam date: String,
        @RequestParam mealType: String,
        @RequestParam index: Int,
        session: HttpSession
    ): String {
        val entryDate = parseDate(date)
        val diary = session.getAttribute("diary") as? MutableMap<LocalDate, DiaryEntry> ?: mutableMapOf()
        val entry = diary[entryDate] ?: return "redirect:/diary?date=$date"
        entry.meals[mealType]?.removeAt(index)
        if (entry.meals[mealType].isNullOrEmpty()) entry.meals.remove(mealType)
        diary[entryDate] = entry
        session.setAttribute("diary", diary)
        return "redirect:/diary?date=$date"
    }

    @PostMapping("/diary/clear-day")
    fun clearDay(@RequestParam date: String, session: HttpSession): String {
        val entryDate = parseDate(date)
        val diary = session.getAttribute("diary") as? MutableMap<LocalDate, DiaryEntry> ?: mutableMapOf()
        diary.remove(entryDate)
        session.setAttribute("diary", diary)
        return "redirect:/diary?date=$date"
    }

    // ---------- Управление предпочтениями ----------
    @GetMapping("/profile/preferences")
    fun preferencesPage(model: Model, session: HttpSession): String {
        val profile = getProfile(session)
        model.addAttribute("favoriteProducts", profile.favoriteProducts.toList())
        model.addAttribute("bannedProducts", profile.bannedProducts.toList())
        return "preferences"
    }

    @PostMapping("/profile/remove-favorite")
    fun removeFavorite(@RequestParam productName: String, session: HttpSession): String {
        val profile = getProfile(session)
        profile.favoriteProducts.remove(productName)
        session.setAttribute("userProfile", profile)
        return "redirect:/profile/preferences"
    }

    @PostMapping("/profile/remove-banned")
    fun removeBanned(@RequestParam productName: String, session: HttpSession): String {
        val profile = getProfile(session)
        profile.bannedProducts.remove(productName)
        session.setAttribute("userProfile", profile)
        return "redirect:/profile/preferences"
    }

    // ---------- Управление произвольными нутриентами (на дашборде) ----------
    @PostMapping("/dashboard/add-custom-target")
    fun addCustomTargetOnDashboard(
        @RequestParam nutrientName: String,
        @RequestParam targetNorm: Double,
        @RequestParam minNorm: Double,
        @RequestParam maxNorm: Double,
        session: HttpSession
    ): String {
        if (nutrientName.isNotBlank() && targetNorm > 0) {
            val profile = getProfile(session)
            profile.customTargets[nutrientName] = Triple(targetNorm, minNorm, maxNorm)
            session.setAttribute("userProfile", profile)
        }
        return "redirect:/dashboard"
    }

    @PostMapping("/dashboard/remove-custom-target")
    fun removeCustomTargetFromDashboard(@RequestParam nutrientName: String, session: HttpSession): String {
        val profile = getProfile(session)
        profile.customTargets.remove(nutrientName)
        session.setAttribute("userProfile", profile)
        return "redirect:/dashboard"
    }
}