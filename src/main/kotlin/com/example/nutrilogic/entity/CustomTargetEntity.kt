package com.example.nutrilogic.entity

import jakarta.persistence.*

@Entity
@Table(name = "custom_targets")
class CustomTargetEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,

    @Column(nullable = false)
    var nutrientName: String,

    @Column(nullable = false)
    var targetNorm: Double,

    var minNorm: Double = 0.0,

    var maxNorm: Double = 0.0
)