package com.example.nutrilogic.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "consumed_products")
class ConsumedProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "diary_entry_id", nullable = false)
    var diaryEntry: DiaryEntryEntity,

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    var product: ProductEntity,

    @Column(nullable = false)
    var quantity: Double,

    @Column(nullable = false)
    var mealType: String,

    @Column(nullable = false)
    var calories: Double,

    @JdbcTypeCode(SqlTypes.JSON)
    var nutrients: MutableMap<String, Double> = mutableMapOf()
)