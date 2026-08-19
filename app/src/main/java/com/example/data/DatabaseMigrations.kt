package com.example.data

import androidx.room.migration.Migration

/**
 * هجرات قاعدة البيانات.
 *
 * القائمة فارغة حالياً لأن الإصدار الحالي هو 3 ولم تُصدَّر مخططات الإصدارات
 * السابقة (كان `exportSchema = false`)، فلا يمكن كتابة هجرات صحيحة بأثر رجعي.
 *
 * القاعدة الملزمة لأي تعديل مستقبلي على المخطط:
 *   1. زد `version` في `AppDatabase` بمقدار واحد.
 *   2. أضف `Migration(oldVersion, newVersion)` هنا بعبارات `ALTER TABLE` الصحيحة.
 *   3. أضفها إلى `ALL` أدناه.
 *
 * لا تُعِد إضافة `fallbackToDestructiveMigration()` — فهي تمسح بيانات المركز
 * بالكامل بصمت عند أول تحديث للمخطط.
 */
object DatabaseMigrations {
  val ALL: Array<Migration> = emptyArray()
}
