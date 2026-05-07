package com.example.nutrilogic.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "diary_entries")
class DiaryEntryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,

    @Column(nullable = false)
    var date: LocalDate,

    @OneToMany(mappedBy = "diaryEntry", cascade = [CascadeType.ALL], orphanRemoval = true)
    var consumedProducts: MutableList<ConsumedProductEntity> = mutableListOf()
)