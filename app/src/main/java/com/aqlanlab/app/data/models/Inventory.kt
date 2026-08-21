package com.aqlanlab.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class InventoryTransactionType(val titleAr: String, val isAddition: Boolean) {
  STOCK_IN("توريد جديد / إضافة رصيد", true),
  USAGE_OUT("صرف واستهلاك معملي/عيادي", false),
  ADJUSTMENT_ADD("تسوية جرد (زيادة)", true),
  ADJUSTMENT_SUBTRACT("تسوية جرد (عجز/نقص)", false),
  RETURN("مرتجع لمورد", false)
}

@Entity(tableName = "inventory_items")
data class InventoryItem(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val category: String, // مواد الطبعات، الخزف والزركونيا، الجبس والشمع، الأكريليك والتعويضات، الاستهلاكيات والمواد اللاصقة
  val currentStock: Double,
  val minThreshold: Double, // تنبيه عند وصول الرصيد لهذا الحد أو أقل
  val reorderQuantity: Double = 5.0, // الكمية الموصى بإعادة طلبها
  val unit: String = "علبة", // علبة، كيس، قرص، عبوة، أنبوب، طقم، قطعة
  val unitCost: Double = 0.0,
  val supplierName: String = "",
  val supplierPhone: String = "",
  val location: String = "مخزن العيادة",
  val lastRestockedDate: Long = System.currentTimeMillis(),
  val expiryDate: Long? = null,
  val notes: String = ""
) {
  val isLowStock: Boolean
    get() = currentStock <= minThreshold

  val isOutOfStock: Boolean
    get() = currentStock <= 0.0

  val totalValue: Double
    get() = currentStock * unitCost

  val stockHealthPercent: Float
    get() {
      val target = (minThreshold * 2.0).coerceAtLeast(1.0)
      return (currentStock / target).toFloat().coerceIn(0f, 1f)
    }
}

@Entity(tableName = "inventory_transactions")
data class InventoryTransaction(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val itemId: Long,
  val itemName: String,
  val type: InventoryTransactionType,
  val quantityChange: Double,
  val newStockLevel: Double,
  val date: Long = System.currentTimeMillis(),
  val performedByUserId: Long = 1,
  val performedByName: String = "Admin",
  val reasonOrReference: String = ""
)
