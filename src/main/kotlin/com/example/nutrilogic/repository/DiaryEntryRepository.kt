package com.example.nutrilogic.repository

import com.example.nutrilogic.entity.DiaryEntryEntity
import com.example.nutrilogic.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DiaryEntryRepository : JpaRepository<DiaryEntryEntity, Long> {
    fun findByUserAndDate(user: UserEntity, date: LocalDate): DiaryEntryEntity?
}