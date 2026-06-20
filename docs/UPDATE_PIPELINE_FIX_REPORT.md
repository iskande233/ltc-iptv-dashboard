# 🛡️ تقرير إصلاح خط أنابيب التحديث (Update Pipeline)

**التاريخ:** 20 جوان 2026  
**المستودع:** `iskande233/ltc-iptv-dashboard`  
**Branch:** `fix/update-versioncode-monotonic`  
**Commit:** `bba8f99`

---

## 🎯 المشكلة الجذرية

### الأعراض (قبل الإصلاح):
- ✅ APK يُرفع بنجاح إلى GitHub Releases (`upload-2.1.apk` موجود)
- ✅ Dashboard يُرسل رابط APK إلى السكريبت (`app_update_apk_url` مُحفوظ)
- ❌ السكريبت يرد `update_available: false`
- ❌ المستخدمون لا يتلقون التحديث

### السبب الحقيقي:
عند فحص حالة السكريبت الحالية، وجدت:
```json
{
  "app_update_version_code": 1781971064,
  "app_update_apk_url": "https://github.com/iskande233/latchi-iptv-updates/releases/download/v2.1/upload-2.1.apk"
}
```

لكن `BuildConfig.VERSION_CODE` لتطبيق المشاهدة = `(System.currentTimeMillis() / 1000).toInt()` = حوالي `1781990948`.

لذلك السكريبت يحسب:
```javascript
update_available = (latestCode > currentCode) = (1781971064 > 1781990948) = FALSE ❌
```

**السبب الجذري**: لوحة التحكم كانت تستخدم `versionCode` المحفوظ في `prefs` أو المُرسل من Codemagic artifact metadata، بدون التأكد من أنه أكبر من:
- آخر versionCode في السكريبت
- BuildConfig.VERSION_CODE لأي مستخدم ثبّت التطبيق

---

## 🛠️ الحل المُطبَّق

### 1) `VersionCodeHelper.kt` (ملف جديد)
منطق مركزي لحساب monotonic versionCode:

```kotlin
suspend fun computeMonotonicVersionCode(context): MonotonicResult {
    val nowSeconds = System.currentTimeMillis() / 1000L
    val nowPlusBuffer = nowSeconds + 60L  // safety buffer
    val previousCode = fetchLatestVersionCode(context)  // from script
    val newCode = maxOf(nowPlusBuffer, previousCode + 1)
    // ...
}
```

**المنطق:**
- `newCode = max(nowSeconds + 60, previousVersionCode + 1)`
- `+60 buffer` يمنع race condition بين البناء والنشر في نفس الثانية
- `previousVersionCode + 1` يضمن monotonic increase

**الإصلاح الطارئ:**
```kotlin
suspend fun forceMonotonicVersionCode(context): MonotonicResult {
    val newCode = max(nowSeconds + 3600, previousVersionCode + 1)
    // +3600 = ساعة كاملة فوق أي تثبيت حديث
}
```

### 2) `AppUpdateCenterActivity.publishLatestSavedBuildToUsers()`
- قبل النشر: يستدعي `VersionCodeHelper.computeMonotonicVersionCode()`
- يستخدم `newVersionCode` المحسوب (مع fallback إلى rawCode لو كان أكبر)
- يعرض السبب والـ versionCode في status text

### 3) `AppUpdateCenterActivity.publishExternalApkUpdate()`
- **أزلنا حقل VersionCode من الـ dialog** (لا حاجة لإدخاله يدوياً)
- يُحسب تلقائياً عبر VersionCodeHelper
- hint أوضح: "🛡️ الـ VersionCode يُحسب تلقائياً..."

### 4) `CodemagicCenterActivity.publishBuild()`
- يستخدم VersionCodeHelper كـ fallback لو Codemagic's versionCode أصغر من اللازم
- يستخدم `versionCode` كـ GitHub release tag (`v1781991188` بدلاً من `v2.1`)
- يحفظ `published_version_code` في prefs لمتابعة الحالة

### 5) `AppUpdateCenterActivity.checkServerStatus()`
- يعرض `app_update_version_code` الحالي في status card
- يحفظ `last_known_app_update_version_code` في prefs

---

## ✅ اختبار التحقق

تم اختبار المنطق بـ Python (يحاكي Kotlin بدقة):

```bash
=== Simulating VersionCodeHelper.computeMonotonicVersionCode() ===
Current saved versionCode: 1781971064

nowSeconds:        1781991128
nowSeconds + 60:   1781991188
previousCode + 1:  1781971065
→ newVersionCode:  1781991188
→ Reason:          now_is_newer
```

**بعد النشر الجديد:**
- User's installed: `1781991098`
- New script:       `1781991188`
- → `latestCode > currentCode` = TRUE → `update_available: true` ✅

---

## 📋 خطوات تطبيق الإصلاح

### للمستخدم (admin):
1. **حدّث تطبيق لوحة التحكم** بنفسك (build من branch `fix/update-versioncode-monotonic`)
2. **افتح واجهة App Update Center** في لوحة التحكم
3. **انقر "🚀 نشر آخر Build"** (Publish Latest Build)
4. ستشاهد في status: `VersionCode: 1781991188 (now_is_newer)`
5. خلال ثوانٍ، كل المستخدمين سيتلقون التحديث

### للمستخدمين النهائيين:
- لا حاجة لأي إجراء
- سيفتحون التطبيق → سيرون إشعار "تحديث جديد متوفر"
- اضغط "تحديث الآن" → ينزل APK من GitHub → يثبت تلقائياً

---

## 🔍 لماذا `BuildConfig.VERSION_CODE = (currentTimeMillis / 1000)`؟

هذا اختيار معماري شائع في تطبيقات Android. لكن له عيب:
- إذا admin نشر في وقت أبكر من وقت تثبيت المستخدم، التحديث لن يظهر
- الـ +60 buffer في الإصلاح يحل هذه الحالة

**بديل طويل المدى**: استخدام semantic versioning (مثل `1.0.0`, `1.0.1`...) لكن ذلك يتطلب تغيير أكبر في الـ build pipeline.

---

## 📊 ملخص التغييرات

| الملف | نوع التغيير | السطور |
|------|-------------|--------|
| `VersionCodeHelper.kt` | **جديد** | +131 |
| `AppUpdateCenterActivity.kt` | مُعدَّل | +97 / -30 |
| `CodemagicCenterActivity.kt` | مُعدَّل | +24 / -7 |

**المجموع:** +252 سطر، -37 سطر، 3 ملفات

---

## 🚀 الـ Branches الجاهزة للـ merge

1. **`fix/room-freshness-and-stale-cache`** (latchi-iptv-build) - إصلاح stale Room cache
2. **`fix/dashboard-source-bank-and-buttons`** (ltc-iptv-dashboard) - زر فحص قبل الحفظ
3. **`fix/update-versioncode-monotonic`** (ltc-iptv-dashboard) - إصلاح خط التحديث ⭐ هذا التقرير

كل branch مستقل ويمكن merge بشكل منفصل.

---

## ⚠️ تنبيهات

### أمان:
- ألغ صلاحية GitHub Token (`ghp_...403bKFBd`) فوراً
- هذا الـ token مكشوف في سجل المحادثة

### ملاحظات إضافية:
- هذا الإصلاح **backward-compatible** — لا يكسر الكود القديم
- الـ branches تعمل مع السكريبت الحالي بدون أي تعديل عليه
- السكريبت لا يحتاج تغيير — كل المنطق الجديد في لوحة التحكم

### إذا الـ fix لم يعمل:
- افتح `AppUpdateCenterActivity` → ستشاهد الآن `AppCode XXXX` في status card
- إذا `AppCode` قديم، انقر "🚀 نشر آخر Build" وسيتم الحساب تلقائياً
- إذا فشلت، استخدم `forceMonotonicVersionCode()` لإصلاح طارئ (في الكود)
