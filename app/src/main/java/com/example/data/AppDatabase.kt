package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.*
import com.example.data.models.*
import kotlinx.coroutines.CoroutineScope

@Database(
  entities = [
    User::class,
    Laboratory::class,
    WorkType::class,
    LabPrice::class,
    Shipment::class,
    Payment::class,
    AuditLog::class,
    AppSetting::class,
    InventoryItem::class,
    InventoryTransaction::class
  ],
  version = 3,
  exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun labDao(): LabDao
  abstract fun workTypeDao(): WorkTypeDao
  abstract fun labPriceDao(): LabPriceDao
  abstract fun shipmentDao(): ShipmentDao
  abstract fun paymentDao(): PaymentDao
  abstract fun auditLogDao(): AuditLogDao
  abstract fun userDao(): UserDao
  abstract fun settingDao(): SettingDao
  abstract fun inventoryDao(): InventoryDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "dental_lab_database.db"
        )
          // ملاحظة أمان بيانات مهمة:
          // كان هنا سابقاً `fallbackToDestructiveMigration()` وهو يعني أن أي زيادة
          // في رقم إصدار قاعدة البيانات تمسح كامل بيانات المركز (الإرساليات،
          // المدفوعات، حسابات المعامل، المخزون) بصمت ودون أي تحذير للمستخدم.
          // أُزيل عمداً: أي إصدار جديد يجب أن يأتي بهجرة `Migration` صريحة في
          // `DatabaseMigrations.ALL`. تصدير المخطط مفعّل (exportSchema = true)
          // ليتمكن Room من التحقق من صحة الهجرات وقت البناء.
          .addMigrations(*DatabaseMigrations.ALL)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
