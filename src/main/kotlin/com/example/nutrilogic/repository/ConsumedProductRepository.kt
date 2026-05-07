package com.example.nutrilogic.repository

import com.example.nutrilogic.entity.ConsumedProductEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ConsumedProductRepository : JpaRepository<ConsumedProductEntity, Long>