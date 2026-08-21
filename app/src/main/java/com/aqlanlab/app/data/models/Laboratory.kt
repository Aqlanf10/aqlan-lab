package com.aqlanlab.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LabStatus(val titleAr: String) {
  ACTIVE("نشط"),
  SUSPENDED("موقوف")
}

@Entity(tableName = "laboratories")
data class Laboratory(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val phone: String = "",
  val address: String = "",
  val managerName: String = "",
  val offeredWorkTypes: String = "", // comma-separated names
  val defaultCurrency: String = "SAR", // YER, SAR, USD
  val status: LabStatus = LabStatus.ACTIVE,
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis()
)
