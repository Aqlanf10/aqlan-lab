package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_types")
data class WorkType(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val nameAr: String,
  val nameEn: String = "",
  val description: String = "",
  val defaultPrice: Double = 0.0,
  val isActive: Boolean = true,
  val category: String = "Fixed Prosthetics" // Fixed, Removable, Orthodontics, Implant
)

@Entity(
  tableName = "lab_prices",
  indices = [androidx.room.Index(value = ["labId", "workTypeId"], unique = true)]
)
data class LabPrice(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val labId: Long,
  val workTypeId: Long,
  val customPrice: Double
)
