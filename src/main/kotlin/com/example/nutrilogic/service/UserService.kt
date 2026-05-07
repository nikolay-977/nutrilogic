package com.example.nutrilogic.service

import com.example.nutrilogic.entity.CustomTargetEntity
import com.example.nutrilogic.entity.ProductEntity
import com.example.nutrilogic.entity.UserEntity
import com.example.nutrilogic.repository.CustomTargetRepository
import com.example.nutrilogic.repository.ProductRepository
import com.example.nutrilogic.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val customTargetRepository: CustomTargetRepository
) {

    // ==================== Базовые операции ====================

    fun getUserByGithubId(githubId: String): UserEntity? =
        userRepository.findByGithubId(githubId)

    fun updateUser(user: UserEntity): UserEntity =
        userRepository.save(user)

    fun createOrUpdateFromOAuth2(githubId: String, username: String, name: String): UserEntity {
        var user = userRepository.findByGithubId(githubId)
        if (user == null) {
            user = UserEntity(
                githubId = githubId,
                username = username,
                name = name.ifBlank { username }
            )
        } else {
            user.username = username
            user.name = name.ifBlank { user.name }
        }
        return userRepository.save(user)
    }

    // ==================== Избранное ====================

    fun addFavoriteProduct(user: UserEntity, productName: String) {
        val product = productRepository.findByName(productName)
            ?: throw IllegalArgumentException("Product not found: $productName")
        if (!user.favoriteProducts.contains(product)) {
            user.favoriteProducts.add(product)
            userRepository.save(user)
        }
    }

    fun removeFavoriteProduct(user: UserEntity, productName: String) {
        val product = productRepository.findByName(productName)
        if (product != null) {
            user.favoriteProducts.remove(product)
            userRepository.save(user)
        }
    }

    // ==================== Чёрный список (запрещённые) ====================

    fun addBannedProduct(user: UserEntity, productName: String) {
        val product = productRepository.findByName(productName)
            ?: throw IllegalArgumentException("Product not found: $productName")
        if (!user.bannedProducts.contains(product)) {
            user.bannedProducts.add(product)
            // Если продукт был в избранном, удаляем оттуда
            user.favoriteProducts.remove(product)
            userRepository.save(user)
        }
    }

    fun removeBannedProduct(user: UserEntity, productName: String) {
        val product = productRepository.findByName(productName)
        if (product != null) {
            user.bannedProducts.remove(product)
            userRepository.save(user)
        }
    }

    // ==================== Кастомные цели по нутриентам ====================

    fun addCustomTarget(
        user: UserEntity,
        nutrientName: String,
        targetNorm: Double,
        minNorm: Double = 0.0,
        maxNorm: Double = 0.0
    ) {
        // Удаляем старую цель, если была
        removeCustomTarget(user, nutrientName)
        val target = CustomTargetEntity(
            user = user,
            nutrientName = nutrientName,
            targetNorm = targetNorm,
            minNorm = minNorm,
            maxNorm = maxNorm
        )
        customTargetRepository.save(target)
    }

    fun removeCustomTarget(user: UserEntity, nutrientName: String) {
        customTargetRepository.deleteByUserAndNutrientName(user, nutrientName)
    }

    fun getCustomTargets(user: UserEntity): Map<String, Triple<Double, Double, Double>> {
        return customTargetRepository.findByUser(user)
            .associate {
                it.nutrientName to Triple(it.targetNorm, it.minNorm, it.maxNorm)
            }
    }
}