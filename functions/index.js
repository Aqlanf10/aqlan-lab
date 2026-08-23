/**
 * Firebase Cloud Functions for Aqlan Dental Lab Management System
 * Production-ready User Provisioning & Administration with Firebase Admin SDK
 *
 * Security Invariants:
 * 1. Only verified SUPER_ADMIN callers can provision or manage users.
 * 2. Real Firebase Auth accounts are created with encrypted temporary passwords.
 * 3. Authorized users are keyed exclusively by Firebase UID.
 * 4. Custom Claims (role, permissions) are set strictly backend-side.
 * 5. Atomic Rollback: Any partial failure during user creation deletes the created Auth account.
 * 6. Detailed audit logs are written for all administrative actions.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const auth = admin.auth();
const DEFAULT_CLINIC_ID = "clinic_main";
const MASTER_DOCTOR_EMAIL = "aqlanf10@gmail.com";

/**
 * Validates that the function caller has SUPER_ADMIN authorization.
 * Also enforces Firebase App Check (defense-in-depth: requests without a valid
 * App Check attestation are rejected even before console-level enforcement).
 */
function assertSuperAdmin(context) {
  // HARDENING: reject calls that do not carry an App Check token
  if (context.app == null) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "The function must be called from an attested app."
    );
  }

  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "يجب تسجيل الدخول أولاً لتنفيذ هذه العملية."
    );
  }

  const token = context.auth.token;
  const isSuperAdminClaim = token.role === "SUPER_ADMIN";
  const isMasterEmail = !!(token.email && token.email.toLowerCase() === MASTER_DOCTOR_EMAIL);

  if (!isSuperAdminClaim && !isMasterEmail) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "غير مصرح لك بتنفيذ هذه العملية. هذه الصلاحية مخصصة للمشرف العام فقط."
    );
  }
}

/**
 * Wraps internal error details: logs the real error server-side and returns a
 * generic Arabic message to the client (previously raw `error.message` was leaked).
 */
function internalError(actionAr, error) {
  console.error(`${actionAr} failed:`, error);
  return new functions.https.HttpsError("internal", actionAr);
}

/**
 * Callable Function: createAuthorizedUser
 * Provisions a new user account with Auth User, Custom Claims, Firestore document, and Audit Log.
 * Implements atomic rollback if any downstream step fails.
 */
exports.createAuthorizedUser = functions.https.onCall(async (data, context) => {
  assertSuperAdmin(context);

  const {
    username,
    email,
    temporaryPassword,
    fullName,
    role = "STAFF",
    permissions = ["read:shipments", "write:shipments"],
    maxDevices = 2,
    clinicId = DEFAULT_CLINIC_ID
  } = data;

  // Validation
  if (!email || !email.includes("@")) {
    throw new functions.https.HttpsError("invalid-argument", "البريد الإلكتروني المدخل غير صالح.");
  }
  // HARDENING: minimum password length raised from 6 to 10 characters
  if (!temporaryPassword || temporaryPassword.length < 10) {
    throw new functions.https.HttpsError("invalid-argument", "كلمة المرور المؤقتة يجب أن لا تقل عن 10 خانات.");
  }
  if (!username || username.trim().length < 3) {
    throw new functions.https.HttpsError("invalid-argument", "اسم المستخدم يجب أن لا يقل عن 3 أحرف.");
  }

  const cleanEmail = email.trim().toLowerCase();
  const cleanUsername = username.trim().toLowerCase();
  const cleanFullName = (fullName || cleanUsername).trim();
  const callerUid = context.auth.uid;
  const callerEmail = context.auth.token.email || "SUPER_ADMIN";

  // Check for duplicate username in Firestore
  const existingUsernameSnap = await db
    .collection("clinics")
    .document(clinicId)
    .collection("authorized_users")
    .where("username", "==", cleanUsername)
    .limit(1)
    .get();

  if (!existingUsernameSnap.empty) {
    throw new functions.https.HttpsError("already-exists", `اسم المستخدم "${cleanUsername}" مسجل مسبقاً.`);
  }

  let createdUserRecord = null;

  try {
    // 1. Create Firebase Authentication User
    createdUserRecord = await auth.createUser({
      email: cleanEmail,
      password: temporaryPassword,
      displayName: cleanFullName,
      disabled: false,
      emailVerified: true
    });

    const newUid = createdUserRecord.uid;

    // 2. Assign Custom Claims
    await auth.setCustomUserClaims(newUid, {
      role: role,
      permissions: permissions
    });

    // 3. Write Firestore Document (keyed by Firebase UID)
    const userDoc = {
      uid: newUid,
      username: cleanUsername,
      fullName: cleanFullName,
      email: cleanEmail,
      role: role,
      isActive: true,
      isApproved: true,
      permissions: permissions,
      maxDevices: Number(maxDevices) || 2,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      createdBy: callerEmail
    };

    await db
      .collection("clinics")
      .document(clinicId)
      .collection("authorized_users")
      .document(newUid)
      .set(userDoc);

    // 4. Create Audit Trail Log
    await db
      .collection("clinics")
      .document(clinicId)
      .collection("audit_logs")
      .add({
        action: "CREATE_USER",
        targetUid: newUid,
        targetUsername: cleanUsername,
        targetEmail: cleanEmail,
        role: role,
        performedBy: callerEmail,
        callerUid: callerUid,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: `Created user ${cleanFullName} with role ${role} and maxDevices=${maxDevices}`
      });

    return {
      success: true,
      uid: newUid,
      username: cleanUsername,
      email: cleanEmail,
      role: role,
      message: `تم إنشاء حساب المستخدم ${cleanFullName} بنجاح.`
    };
  } catch (error) {
    console.error("Error provisioning user:", error);

    // Atomic Rollback: If Auth user was created but subsequent operations failed, delete Auth user
    if (createdUserRecord && createdUserRecord.uid) {
      try {
        console.warn(`Rolling back created auth user ${createdUserRecord.uid}`);
        await auth.deleteUser(createdUserRecord.uid);
      } catch (rollbackErr) {
        console.error("Rollback failed for UID:", createdUserRecord.uid, rollbackErr);
      }
    }

    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw internalError("حدث خطأ أثناء إنشاء المستخدم.", error);
  }
});

/**
 * Callable Function: setUserActiveStatus
 * Enables or disables a user account and revokes active sessions when disabled.
 */
exports.setUserActiveStatus = functions.https.onCall(async (data, context) => {
  assertSuperAdmin(context);

  const { targetUid, isActive, reason = "", clinicId = DEFAULT_CLINIC_ID } = data;
  if (!targetUid) {
    throw new functions.https.HttpsError("invalid-argument", "معرف المستخدم (UID) مطلوب.");
  }

  const callerEmail = context.auth.token.email || "SUPER_ADMIN";

  try {
    // 1. Update Firebase Auth status
    await auth.updateUser(targetUid, {
      disabled: !isActive
    });

    // 2. If disabling, revoke all active refresh tokens immediately
    if (!isActive) {
      await auth.revokeRefreshTokens(targetUid);
    }

    // 3. Update Firestore Document
    await db
      .collection("clinics")
      .document(clinicId)
      .collection("authorized_users")
      .document(targetUid)
      .update({
        isActive: Boolean(isActive),
        statusReason: reason,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedBy: callerEmail
      });

    // 4. Audit Log
    await db
      .collection("clinics")
      .document(clinicId)
      .collection("audit_logs")
      .add({
        action: isActive ? "ENABLE_USER" : "DISABLE_USER",
        targetUid: targetUid,
        performedBy: callerEmail,
        callerUid: context.auth.uid,
        reason: reason,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      });

    return {
      success: true,
      targetUid: targetUid,
      isActive: Boolean(isActive),
      message: isActive ? "تم تفعيل الحساب بنجاح." : "تم تعطيل الحساب وإنهاء جميع جلساته النشطة."
    };
  } catch (error) {
    throw internalError("فشل تحديث حالة الحساب.", error);
  }
});

/**
 * Callable Function: resetUserPassword
 * Resets user password securely by Super Admin.
 */
exports.resetUserPassword = functions.https.onCall(async (data, context) => {
  assertSuperAdmin(context);

  const { targetUid, newPassword, clinicId = DEFAULT_CLINIC_ID } = data;
  // HARDENING: minimum password length raised from 6 to 10 characters
  if (!targetUid || !newPassword || newPassword.length < 10) {
    throw new functions.https.HttpsError("invalid-argument", "كلمة المرور الجديدة يجب أن لا تقل عن 10 خانات.");
  }

  const callerEmail = context.auth.token.email || "SUPER_ADMIN";

  try {
    await auth.updateUser(targetUid, {
      password: newPassword
    });

    await auth.revokeRefreshTokens(targetUid);

    await db
      .collection("clinics")
      .document(clinicId)
      .collection("audit_logs")
      .add({
        action: "RESET_PASSWORD",
        targetUid: targetUid,
        performedBy: callerEmail,
        callerUid: context.auth.uid,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      });

    return {
      success: true,
      message: "تم إعادة تعيين كلمة المرور بنجاح."
    };
  } catch (error) {
    throw internalError("فشل إعادة تعيين كلمة المرور.", error);
  }
});

/**
 * Callable Function: updateUserRoleAndPermissions
 * Updates user role and custom claims, revoking tokens to force claim refresh.
 */
exports.updateUserRoleAndPermissions = functions.https.onCall(async (data, context) => {
  assertSuperAdmin(context);

  const { targetUid, role, permissions = [], maxDevices, clinicId = DEFAULT_CLINIC_ID } = data;
  if (!targetUid || !role) {
    throw new functions.https.HttpsError("invalid-argument", "المعرف والدور مطلوبان.");
  }

  const callerEmail = context.auth.token.email || "SUPER_ADMIN";

  try {
    // 1. Update Custom Claims
    await auth.setCustomUserClaims(targetUid, {
      role: role,
      permissions: permissions
    });

    // 2. Revoke tokens to force immediate token refresh
    await auth.revokeRefreshTokens(targetUid);

    // 3. Update Firestore Document
    const updates = {
      role: role,
      permissions: permissions,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: callerEmail
    };
    if (maxDevices !== undefined) {
      updates.maxDevices = Number(maxDevices);
    }

    await db
      .collection("clinics")
      .document(clinicId)
      .collection("authorized_users")
      .document(targetUid)
      .update(updates);

    // 4. Audit Log
    await db
      .collection("clinics")
      .document(clinicId)
      .collection("audit_logs")
      .add({
        action: "UPDATE_ROLE",
        targetUid: targetUid,
        newRole: role,
        permissions: permissions,
        performedBy: callerEmail,
        callerUid: context.auth.uid,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      });

    return {
      success: true,
      targetUid: targetUid,
      role: role,
      message: "تم تحديث الدور والصلاحيات بنجاح."
    };
  } catch (error) {
    throw internalError("فشل تحديث الصلاحيات.", error);
  }
});

/**
 * Callable Function: revokeUserSessions
 * Revokes all refresh tokens for a user.
 */
exports.revokeUserSessions = functions.https.onCall(async (data, context) => {
  assertSuperAdmin(context);

  const { targetUid, clinicId = DEFAULT_CLINIC_ID } = data;
  if (!targetUid) {
    throw new functions.https.HttpsError("invalid-argument", "المعرف UID مطلوب.");
  }

  const callerEmail = context.auth.token.email || "SUPER_ADMIN";

  try {
    await auth.revokeRefreshTokens(targetUid);

    await db
      .collection("clinics")
      .document(clinicId)
      .collection("audit_logs")
      .add({
        action: "REVOKE_SESSIONS",
        targetUid: targetUid,
        performedBy: callerEmail,
        callerUid: context.auth.uid,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      });

    return {
      success: true,
      message: "تم إنهاء كافة الجلسات النشطة للمستخدم بنجاح."
    };
  } catch (error) {
    throw internalError("فشل إنهاء الجلسات.", error);
  }
});

/**
 * Validates that caller has financial privileges (SUPER_ADMIN, ADMIN, ACCOUNTANT).
 * STAFF and TECHNICIAN callers are strictly rejected server-side.
 * Also enforces Firebase App Check (defense-in-depth).
 */
function assertFinancialAccess(context) {
  // HARDENING: reject calls that do not carry an App Check token
  if (context.app == null) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "The function must be called from an attested app."
    );
  }

  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول للوصول إلى البيانات المالية.");
  }
  const token = context.auth.token;
  const isMasterEmail = !!(token.email && token.email.toLowerCase() === MASTER_DOCTOR_EMAIL);
  const isFinancialRole = token.role === "SUPER_ADMIN" || token.role === "ADMIN" || token.role === "ACCOUNTANT";
  const hasPerm = Array.isArray(token.permissions) && (token.permissions.includes("read:financials") || token.permissions.includes("write:financials"));

  if (!isMasterEmail && !isFinancialRole && !hasPerm) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "غير مصرح لك بالوصول إلى البيانات والتقارير المالية. هذه الصلاحية مخصصة للإدارة والمحاسبين فقط."
    );
  }
}

/**
 * Callable Function: getShipmentFinancials
 * Returns price, cost, margin, and discount for a shipment.
 * Strictly forbidden for STAFF & TECHNICIAN.
 */
exports.getShipmentFinancials = functions.https.onCall(async (data, context) => {
  assertFinancialAccess(context);

  const { shipmentId, clinicId = DEFAULT_CLINIC_ID } = data;
  if (!shipmentId) {
    throw new functions.https.HttpsError("invalid-argument", "معرف الإرسالية مطلوب.");
  }

  try {
    const docSnap = await db
      .collection("clinics")
      .document(clinicId)
      .collection("shipment_finance")
      .document(String(shipmentId))
      .get();

    if (!docSnap.exists) {
      return { exists: false, financialData: null };
    }

    return {
      exists: true,
      financialData: docSnap.data()
    };
  } catch (error) {
    throw internalError("فشل جلب البيانات المالية.", error);
  }
});

/**
 * Callable Function: saveShipmentFinancials
 * Writes or updates financial details in shipment_finance collection.
 */
exports.saveShipmentFinancials = functions.https.onCall(async (data, context) => {
  assertFinancialAccess(context);

  const {
    shipmentId,
    unitPrice = 0,
    totalPrice = 0,
    discount = 0,
    labPrice = 0,
    cost = 0,
    profit = 0,
    clinicId = DEFAULT_CLINIC_ID
  } = data;

  if (!shipmentId) {
    throw new functions.https.HttpsError("invalid-argument", "معرف الإرسالية مطلوب.");
  }

  const callerEmail = context.auth.token.email || "ACCOUNTANT";

  try {
    const financeDoc = {
      shipmentId: String(shipmentId),
      unitPrice: Number(unitPrice),
      totalPrice: Number(totalPrice),
      discount: Number(discount),
      labPrice: Number(labPrice),
      cost: Number(cost),
      profit: Number(profit),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: callerEmail
    };

    await db
      .collection("clinics")
      .document(clinicId)
      .collection("shipment_finance")
      .document(String(shipmentId))
      .set(financeDoc, { merge: true });

    return {
      success: true,
      message: "تم حفظ البيانات المالية للإرسالية بنجاح."
    };
  } catch (error) {
    throw internalError("فشل حفظ البيانات المالية.", error);
  }
});

/**
 * Callable Function: getAppVersionConfig
 * Returns current minimum supported version and latest release info.
 */
exports.getAppVersionConfig = functions.https.onCall(async (data, context) => {
  try {
    const docSnap = await db.collection("app_config").document("version_config").get();
    if (!docSnap.exists) {
      return {
        minimumSupportedVersionCode: 2,
        latestVersionCode: 2,
        latestVersionName: "1.1.0",
        isMandatory: false
      };
    }
    return docSnap.data();
  } catch (error) {
    throw internalError("فشل قراءة إعدادات الإصدار.", error);
  }
});

/**
 * Callable Function: setMinimumSupportedVersion
 * Allows Super Admin to raise the minimum supported version remotely.
 */
exports.setMinimumSupportedVersion = functions.https.onCall(async (data, context) => {
  assertSuperAdmin(context);

  const {
    minimumSupportedVersionCode = 2,
    latestVersionCode = 2,
    latestVersionName = "1.1.0",
    updateTitleAr = "تحديث أمني إجباري مطلوب",
    updateMessageAr = "يتوفر إصدار أمني جديد ومحدث. يُرجى التحديث للمتابعة وحماية البيانات السحابية.",
    releaseNotesAr = "",
    updateUrl = "https://play.google.com/store/apps/details?id=com.aqlanlab.app"
  } = data;

  // HARDENING: the update URL must be an HTTPS link to an allow-listed distribution
  // host. Previously ANY string was accepted, so a compromised admin session could
  // repoint the mandatory-update button at an arbitrary APK dropper.
  const allowedUrlHosts = [
    "play.google.com",
    "github.com",
    "dentallab-online.app"
  ];
  let safeUpdateUrl = "https://play.google.com/store/apps/details?id=com.aqlanlab.app";
  try {
    const parsed = new URL(String(updateUrl));
    if (parsed.protocol === "https:" && (allowedUrlHosts.includes(parsed.hostname) || parsed.hostname.endsWith(".github.io"))) {
      safeUpdateUrl = parsed.toString();
    } else {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "رابط التحديث يجب أن يكون HTTPS ومن نطاق موثوق (Play Store / GitHub)."
      );
    }
  } catch (e) {
    if (e instanceof functions.https.HttpsError) throw e;
    throw new functions.https.HttpsError("invalid-argument", "رابط التحديث غير صالح.");
  }

  try {
    const configData = {
      minimumSupportedVersionCode: Number(minimumSupportedVersionCode),
      latestVersionCode: Number(latestVersionCode),
      latestVersionName: String(latestVersionName),
      updateTitleAr: String(updateTitleAr),
      updateMessageAr: String(updateMessageAr),
      releaseNotesAr: String(releaseNotesAr),
      updateUrl: safeUpdateUrl,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: context.auth.token.email || "SUPER_ADMIN"
    };

    await db.collection("app_config").document("version_config").set(configData, { merge: true });

    return {
      success: true,
      message: `تم تعيين الحد الأدنى للإصدار المدعوم إلى ${minimumSupportedVersionCode} بنجاح.`
    };
  } catch (error) {
    throw internalError("فشل تحديث الحد الأدنى للإصدار.", error);
  }
});


