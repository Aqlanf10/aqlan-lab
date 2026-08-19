package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ShipmentStatus(val titleAr: String, val stepIndex: Int) {
  NEW("جديدة", 0),
  IN_PROGRESS("قيد العمل", 1),
  READY("جاهزة", 2),
  RECEIVED("تم الاستلام", 3),
  CANCELLED("ملغاة", -1)
}

@Entity(tableName = "shipments")
data class Shipment(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val shipmentNumber: String, // e.g. "#000125"
  val orderDate: Long = System.currentTimeMillis(),
  val clinicOrDoctorName: String,
  val patientName: String = "",
  val labId: Long,
  val labName: String,
  val workTypeId: Long,
  val workTypeName: String,
  val pieceCount: Int = 1,
  val toothNumbers: String = "", // e.g. "11, 12, 21"
  val shade: String = "A2", // e.g. "A2", "A1", "BL2", etc.
  val shadeNotes: String = "", // e.g. "Translucent incisal edge, gingival A3"
  val expectedDeliveryDate: Long = System.currentTimeMillis() + (5 * 24 * 60 * 60 * 1000L), // +5 days default
  val actualReceivedDate: Long? = null,
  val status: ShipmentStatus = ShipmentStatus.NEW,
  val notes: String = "",
  val imageUri: String = "",
  // Financial fields (Admin / Accountant visible ONLY)
  val currency: String = "SAR", // YER, SAR, USD
  val unitPrice: Double = 0.0,
  val totalPrice: Double = 0.0,
  val discount: Double = 0.0,
  val isUrgent: Boolean = false,
  val createdByUserId: Long = 1,
  val createdByName: String = "Admin"
)
