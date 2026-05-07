package com.example.nutrilogic.repository

import com.example.nutrilogic.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByGithubId(githubId: String): UserEntity?
}