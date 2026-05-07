package com.example.nutrilogic.service

import com.example.nutrilogic.entity.ConsumedProductEntity
import com.example.nutrilogic.entity.DiaryEntryEntity
import com.example.nutrilogic.entity.UserEntity
import com.example.nutrilogic.repository.ConsumedProductRepository
import com.example.nutrilogic.repository.DiaryEntryRepository
import com.example.nutrilogic.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class DiaryService(
    private val diaryEntryRepository: DiaryEntryRepository,
    private val consumedProductRepository: ConsumedProductRepository,
    private val productRepository: ProductRepository,
    private val userService: UserService   // для получения кастомных целей
) {

    /**
     * Получить запись дневника за конкретную дату (создать, если не существует)
     */
    fun getDiaryEntry(user: UserEntity, date: LocalDate): DiaryEntryEntity {
        return diaryEntryRepository.findByUserAndDate(user, date)
            ?: DiaryEntryEntity(user = user, date = date).also {
                diaryEntryRepository.save(it)
            }
    }

    /**
     * Добавить продукт в дневник
     */
    fun addConsumedProduct(
        user: UserEntity,
        date: LocalDate,
        productName: String,
        quantity: Double,
        mealType: String
    ) {
        val product = productRepository.findByName(productName)
            ?: throw IllegalArgumentException("Product not found: $productName")

        val entry = getDiaryEntry(user, date)
        val factor = quantity / 100.0

        // Рассчитываем калории и основные нутриенты
        val calories = (product.getCalories() ?: 0.0) * factor
        val protein = (product.getNutrientValue("Белки") ?: 0.0) * factor
        val fat = (product.getNutrientValue("Жиры") ?: 0.0) * factor
        val carbs = (product.getNutrientValue("Углеводы") ?: 0.0) * factor

        // Получаем кастомные цели пользователя
        val customTargets = userService.getCustomTargets(user)

        // Формируем карту всех нутриентов (включая кастомные)
        val nutrientsMap = mutableMapOf<String, Double>().apply {
            put("Калории", calories)
            put("Белки", protein)
            put("Жиры", fat)
            put("Углеводы", carbs)

            customTargets.keys.forEach { nutrientName ->
                put(nutrientName, (product.getNutrientValue(nutrientName) ?: 0.0) * factor)
            }
        }

        val consumed = ConsumedProductEntity(
            diaryEntry = entry,
            product = product,
            quantity = quantity,
            mealType = mealType,
            calories = calories,
            nutrients = nutrientsMap
        )

        entry.consumedProducts.add(consumed)
        diaryEntryRepository.save(entry)
    }

    /**
     * Удалить конкретный ConsumedProduct по его ID
     */
    fun removeConsumedProduct(consumedId: Long) {
        consumedProductRepository.deleteById(consumedId)
    }

    /**
     * Очистить весь день – удалить запись дневника целиком
     */
    fun clearDiaryDay(user: UserEntity, date: LocalDate) {
        diaryEntryRepository.findByUserAndDate(user, date)?.let {
            diaryEntryRepository.delete(it)
        }
    }

    /**
     * Получить все consumed продукты за конкретный день (без группировки)
     */
    fun getConsumedProductsForDay(user: UserEntity, date: LocalDate): List<ConsumedProductEntity> {
        return diaryEntryRepository.findByUserAndDate(user, date)?.consumedProducts ?: emptyList()
    }

    /**
     * Получить сумму потреблённого нутриента за день
     */
    fun getTotalNutrientForDay(user: UserEntity, date: LocalDate, nutrientName: String): Double {
        return getConsumedProductsForDay(user, date).sumOf { it.nutrients[nutrientName] ?: 0.0 }
    }
}