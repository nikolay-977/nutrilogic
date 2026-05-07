package com.example.nutrilogic.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    var githubId: String,

    @Column(nullable = false)
    var username: String,

    var name: String = "",

    var gender: String = "male",

    var birthDate: LocalDate? = null,

    var height: Double = 0.0,

    var weight: Double = 0.0,

    var activityLevel: String = "moderate",

    var targetCalories: Double = 2000.0,

    var targetProtein: Double = 120.0,

    var targetFat: Double = 70.0,

    var targetCarbs: Double = 250.0,

    @ManyToMany
    @JoinTable(
        name = "user_favorite_products",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "product_id")]
    )
    var favoriteProducts: MutableSet<ProductEntity> = mutableSetOf(),

    @ManyToMany
    @JoinTable(
        name = "user_banned_products",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "product_id")]
    )
    var bannedProducts: MutableSet<ProductEntity> = mutableSetOf()
)