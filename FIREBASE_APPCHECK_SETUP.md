# دليل تفعيل Firebase App Check و Play Integrity Provider

هذا الدليل يوضح خطوات تفعيل وحماية تطبيق **مركز الدكتور عقلان الكامل لتقويم وزراعة وتجميل الأسنان** (`com.aqlanlab.app`) باستخدام **Play Integrity** و **Firebase App Check**.

---

## 1. الهوية الرسمية وحزمة التطبيق (Application ID & Package)
* **Application ID / Package Name:** `com.aqlanlab.app`
* **Namespace:** `com.aqlanlab.app`
* **FileProvider Authority:** `com.aqlanlab.app.fileprovider`

---

## 2. كيفية عمل App Check في الكود (Architecture)
تم ضبط الكود تلقائياً ليفصل بين بيئة التطوير وبيئة الإنتاج:

1. **في بيئة الإنتاج (Release Build / Production):**
   * يستخدم التطبيق: `PlayIntegrityAppCheckProviderFactory.getInstance()`
   * يتحقق Google Play Integrity من صحة جهاز أندرويد وتوقيع التطبيق الحقيقي من متجر Google Play.
2. **في بيئة التطوير (Debug Build / Testing):**
   * يستخدم التطبيق: `DebugAppCheckProviderFactory.getInstance()`
   * يقوم الـ SDK بطباعة رمز التحقق `App Check Debug Token` في سطر أوامر `Logcat`، ولا يتم تخزين هذا الرمز في Git نهائياً.

---

## 3. الخطوات المطلوبة في Firebase Console و Google Play Console

### أ. تسجيل التطبيق بالـ Package الجديد:
1. افتح **[Firebase Console](https://console.firebase.google.com/)** واختر مشروعك.
2. في **Project Settings (إعدادات المشروع)**، تأكد من إضافة تطبيق أندرويد بالـ Package Name:
   ```text
   com.aqlanlab.app
   ```
3. حمّل ملف `google-services.json` وضعه في مجلد `/app`.

### ب. ربط شهادة التوقيع (SHA-256 Fingerprint):
1. من **Google Play Console** -> انتقل إلى **Release** -> **Setup** -> **App Integrity** -> **App Signing**.
2. انسخ بصمة **SHA-256 certificate fingerprint** الخاصة بـ App Signing وبصمة الـ Upload key.
3. الصقها في إعدادات التطبيق في **Firebase Console** -> **Project Settings** -> **SHA certificate fingerprints**.

### ج. تفعيل Play Integrity في Firebase App Check:
1. في Firebase Console، من القائمة الجانبية اختر **Build** -> **App Check**.
2. اختر تطبيق `com.aqlanlab.app`.
3. اضغط على **Play Integrity** وأدخل رقم مشروع Google Cloud Project إذا طُلب منك.
4. اضغط **Save**.

### د. إضافة Debug Token لبيئة التطوير (Local Debugging):
1. شغّل التطبيق في وضع الـ Debug.
2. ابحث في `Logcat` عن عبارة:
   ```text
   Enter this debug secret into the allow list in the Firebase Console for your project: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
   ```
3. في **Firebase Console** -> **App Check** -> اضغط على الثلاث نقاط بجانب التطبيق `...` -> **Manage debug tokens**.
4. اضغط **Add debug token** وأدخل الرمز واسم الجهاز التابع لك.

---

## 4. خطة التفعيل التدريجي لفرض الحماية (Enforcement Rollout):

| الخدمة | المرحلة 1 (مراقبة - Monitoring) | المرحلة 2 (الفرض التام - Enforced) |
|---|---|---|
| **Cloud Firestore** | راقب تبويب App Check Metrics للتأكد من وصول الطلبات المعتمدة | اضغط **Enforce** لحجب أي وصول خارج التطبيق الرسمي |
| **Cloud Storage** | راقب طلبات رفع وتنزيل المرفقات | اضغط **Enforce** لحماية ملفات الحالات والنسخ الاحتياطية |
| **Cloud Functions** | راقب دوال الإدارة والتقارير المالية | اضغط **Enforce** لضمان استدعاء الدوال من تطبيق أصلي فقط |

---

## 5. سلوك التطبيق عند فشل التحقق (App Check Failure):
* في حال حاول مخترق أو تطبيق معدل (Modified APK / Rooted bot) الاتصال بالسيرفر بدون توقيع Play Integrity صالح:
  1. يقوم خادم Firebase (Firestore / Storage / Functions) برفض الطلب وإرجاع خطأ `PERMISSION_DENIED` أو `UNAUTHENTICATED`.
  2. يتعامل تطبيق أندرويد مع الخطأ بأمان ويحتفظ بالبيانات محلياً في قاعدة بيانات **Room SQLite** المشفرة دون فقدان أي بيانات للطبيب.
