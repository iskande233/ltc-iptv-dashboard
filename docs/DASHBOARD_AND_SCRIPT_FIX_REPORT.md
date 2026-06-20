# 🎛️ تقرير إصلاح لوحة التحكم + السكريبت

**التاريخ:** 20 جوان 2026  
**المستودعات:** 
- `iskande233/ltc-iptv-dashboard` (branch: `fix/dashboard-source-bank-and-buttons`)
- `iskande233/latchi-iptv-build` (branch: `fix/room-freshness-and-stale-cache`)

---

## 🎯 المشاكل التي تم إصلاحها

### 1) واجهة بنك المصادر كانت مزدحمة ومربكة
**قبل:** زر واحد `💾 حفظ الرابط` يحفظ الرابط مباشرة بدون فحص
**بعد:** 
- زر `🔎 فحص قبل الحفظ` (جديد) يفحص الرابط أولاً
- زر `💾 حفظ الرابط` يحفظ فقط إذا تم الفحص بنجاح
- عرض مباشر للنوع المكتشف أثناء الكتابة (XTREAM / M3U / AUTO)
- عرض نتيجة الفحص (✅ شغال + وقت الاستجابة + تاريخ الانتهاء)

### 2) بعض الأزرار لا تعمل بشكل واضح
**قبل:** لا توجد رسائل واضحة عند الفشل أو النجاح
**بعد:** 
- كل زر له رسالة Toast + status text + alert overlay
- التحقق من صيغة الرابط قبل أي عملية
- تحذير قبل الحفظ إذا لم يتم الفحص

### 3) السكريبت لم يكن يفحص المصدر قبل التعميم
**قبل:** `adminUpdateMasterUrl_` يحفظ أي رابط دون التحقق
**بعد:** 
- أضفنا `inspect_source` endpoint يفحص Xtream و M3U
- `adminUpdateMasterUrl_` يستخدم `inspectM3uSource_` للتحقق من الرابط
- رسائل واضحة في حالة فشل الفحص (لكن لا نمنع — قد يكون admin يعرف ما يفعل)
- `adminUploadPreparedCatalog_` يفحص JSON قبل الرفع + يتحقق من الحجم (max 5MB)

### 4) Per-type freshness (تم في الخطوة السابقة)
- `get_live_master_state` يعيد الآن `catalog_revision_live`, `catalog_revision_bein`, إلخ
- `adminUploadPreparedCatalog_` يرفع server_revision الشامل تلقائياً بعد كل رفع

---

## 📁 الملفات المعدّلة

### لوحة التحكم (`ltc-iptv-dashboard`)
| الملف | التغييرات |
|------|-----------|
| `activity_category_visibility_control.xml` | +63 سطر (Live status row + فحص/حفظ buttons) |
| `CategoryVisibilityControlActivity.kt` | +156 سطر (testNewSourceBeforeSave, isValidUrl, إلخ) |

**Branch:** `fix/dashboard-source-bank-and-buttons`  
**Commit:** `2fe8c83`  
**PR:** https://github.com/iskande233/ltc-iptv-dashboard/pull/new/fix/dashboard-source-bank-and-buttons

### السكريبت (`Code.gs.updated (5).txt`)
- إضافة `handleInspectSource_` (endpoint جديد)
- إضافة `inspectXtreamSource_` + `inspectM3uSource_`
- تحديث `adminUpdateMasterUrl_` ليستخدم الفحص
- تحديث `adminUploadPreparedCatalog_` للتحقق من JSON + الحجم
- إضافة `catalog_revision_*` في `handleGetLiveMasterState_`
- رفع `server_revision` تلقائياً في `adminUploadPreparedCatalog_`

---

## 🛡️ التدفق الجديد لبنك المصادر

```
┌─────────────────────────────────────────┐
│ Card: بنك المصادر المحلي                │
│ ┌─────────────────────────────────────┐ │
│ │ اسم الرابط: [Server Stable 01]      │ │
│ │ رابط السيرفر: [http://...]         │ │
│ │                                     │ │
│ │ 🔍 النوع: XTREAM (Xtream Codes)    │ │ ← live detection
│ │ ⚡ لم يتم الفحص بعد                │ │ ← live status
│ │                                     │ │
│ │ ┌──────────┬──────────┐              │ │
│ │ │ 🔎 فحص  │ 💾 حفظ   │              │ │ ← buttons
│ │ └──────────┴──────────┘              │ │
│ │ ⚡ فحص كل الروابط المحفوظة         │ │
│ │                                     │ │
│ │ 📡 المصدر المعمم الحالي: ...        │ │
│ │ 📋 روابط محفوظة: ...                │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### خطوات الاستخدام:
1. **اكتب اسم + رابط** → النظام يكشف النوع تلقائياً (live detection)
2. **اضغط 🔎 فحص قبل الحفظ** → يفحص الرابط ويعرض النتيجة
3. **إذا ✅ شغال** → اضغط 💾 حفظ ليتم الحفظ
4. **إذا ❌ فشل** → النظام يحذرك ويسألك إذا أردت الحفظ رغم ذلك

---

## 🔍 الـ Endpoints الجديدة في السكريبت

### `inspect_source` (Admin فقط)
```
GET https://script.google.com/.../exec?action=inspect_source&secret=X&url=Y
```

**Response:**
```json
{
  "success": true,
  "online": true,
  "valid": true,
  "type": "xtream" | "m3u",
  "url": "...",
  "responseMs": 245,
  "hasLive": true,
  "hasMovies": true,
  "hasSeries": true,
  "channelCount": 1234,
  "serverInfo": {
    "username": "user",
    "status": "Active",
    "expiry": "2026-12-31"
  }
}
```

### `get_live_master_state` (محسّن)
يضيف الآن:
- `catalog_revision_live`
- `catalog_revision_bein`
- `catalog_revision_movies`
- `catalog_revision_series`

---

## ⚙️ كيفية تطبيق السكريبت المُحدَّث

### الطريقة 1: Update Deployment (موصى بها)
1. افتح script.google.com → افتح السكريبت الحالي
2. استبدل الكود بالكامل بمحتوى `Code.gs.updated (5).txt`
3. **Deploy → Manage deployments**
4. اضغط على ✏️ (edit) بجانب الـ deployment النشط
5. **Version → New version**
6. اضغط **Deploy**
7. ✅ نفس الـ URL — لا حاجة لتحديث التطبيقات!

### الطريقة 2: Manual update
إذا لم يكن هناك deployment، أنشئ deployment جديد وانسخ الـ URL إلى:
- `ActivationConfig.kt` في `latchi-iptv-build`
- `DEFAULT_API_URL` في `ltc-iptv-dashboard`

---

## ✅ اختبارات السكريبت (validate)

```bash
# Test inspect_source (سيتم في Apps Script editor)
function testInspect() {
  const url = "https://script.google.com/.../exec?action=inspect_source&secret=LatchiAdmin2026&url=http://test.com/get.php?username=u&password=p";
  const res = UrlFetchApp.fetch(url);
  Logger.log(res.getContentText());
}
```

---

## 🚀 ملخص الـ branches المنشأة

| Branch | المستودع | الوصف |
|--------|----------|------|
| `fix/room-freshness-and-stale-cache` | `latchi-iptv-build` | إصلاح stale Room cache (تم سابقاً) |
| `fix/dashboard-source-bank-and-buttons` | `ltc-iptv-dashboard` | إصلاح واجهة بنك المصادر + أزرار |

كلا الـ branches جاهزة للـ merge بعد اختبار.

---

## 📝 ملاحظات للـ admin

### عند ترقية السكريبت:
- ✅ نفس الـ URL → لا حاجة لتحديث أي تطبيق
- ✅ البيانات القديمة ستبقى كما هي
- ✅ endpoints الجديدة متاحة فوراً

### عند استخدام لوحة التحكم:
- اكتب اسم + رابط في الحقول الجديدة
- **افحص قبل الحفظ دائماً** (يقلل من الروابط الميتة)
- استخدم `⭐ رسمي` لتعيين الرابط الرسمي
- `🚀 تعميم مباشر` يرفع server_revision تلقائياً

---

## ✅ الخلاصة

| الميزة | الحالة |
|--------|--------|
| زر "فحص قبل الحفظ" | ✅ مضاف |
| عرض النوع المكتشف live | ✅ مضاف |
| التحقق من صيغة الرابط | ✅ مضاف |
| تحذير قبل الحفظ دون فحص | ✅ مضاف |
| `inspect_source` endpoint | ✅ مضاف |
| فحص JSON قبل رفع catalog | ✅ مضاف |
| per-type catalog_revision في get_live_master_state | ✅ مضاف |
| رفع server_revision تلقائياً بعد رفع catalog | ✅ مضاف |

كل التغييرات backward-compatible — السكريبت الجديد يعمل مع التطبيقات القديمة.
