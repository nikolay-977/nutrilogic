package com.example.nutrilogic.repository

import com.example.nutrilogic.entity.CustomTargetEntity
import com.example.nutrilogic.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CustomTargetRepository : JpaRepository<CustomTargetEntity, Long> {
    fun findByUser(user: UserEntity): List<CustomTargetEntity>
    fun deleteByUserAndNutrientName(user: UserEntity, nutrientName: String)
}