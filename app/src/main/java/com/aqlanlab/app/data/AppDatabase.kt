package com.aqlanlab.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aqlanlab.app.data.dao.*
import com.aqlanlab.app.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    InventoryTransaction::class,
    DeviceBinding::class
  ],
  version = 5,
  exportSchema = false
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
  abstract fun deviceBindingDao(): DeviceBindingDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "dental_lab_database.db"
        )
        .addCallback(DatabaseCallback(scope))
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }

    private class DatabaseCallback(
      private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
          scope.launch(Dispatchers.IO) {
            populateDatabase(database)
          }
        }
      }

      suspend fun populateDatabase(database: AppDatabase) {
        database.userDao().insertAll(DatabaseSeedData.defaultUsers)
        database.labDao().insertAll(DatabaseSeedData.defaultLabs)
        database.workTypeDao().insertAll(DatabaseSeedData.defaultWorkTypes)
        database.labPriceDao().insertAll(DatabaseSeedData.defaultLabPrices)
        database.shipmentDao().insertAll(DatabaseSeedData.defaultShipments)
        database.paymentDao().insertAll(DatabaseSeedData.defaultPayments)
        database.auditLogDao().insertAll(DatabaseSeedData.defaultAuditLogs)
        database.deviceBindingDao().insertAll(DatabaseSeedData.defaultDevices)
        for (setting in DatabaseSeedData.defaultSettings) {
          database.settingDao().setSetting(setting)
        }
      }
    }
  }
}
