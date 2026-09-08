# MegaVPN Admin

پنل حرفه‌ای مدیریت کانفیگ‌ها با Cloudflare Worker + D1 آماده است.

## قابلیت‌ها
- ورود مدیر با `ADMIN_TOKEN`
- Paste انواع VLESS / VMess / Trojan / Hysteria2 / TUIC / Shadowsocks / URI / Base64 / V2Ray-Xray JSON
- افزودن، ویرایش و حذف
- فعال/غیرفعال کردن در سطح دیتابیس
- API عمومی فقط برای کلاینت: `/api/client/subscription`
- API مدیریت با Bearer token
- ذخیره امن‌تر از قرار دادن لیست کانفیگ در APK

## راه‌اندازی

1. در Cloudflare یک D1 Database با نام `megavpn` بساز.
2. مقدار `database_id` در `wrangler.toml` را با ID واقعی جایگزین کن.
3. از همین پوشه، `schema.sql` را روی D1 اجرا کن.
4. فایل `index.html` را داخل `worker.js` جایگزین placeholder `__ADMIN_HTML__` کن. برای جلوگیری از escape دستی می‌توانی با اسکریپت کوچک Node/Python آن را داخل template string قرار بدهی.
5. Worker را Deploy کن.
6. یک secret با نام `ADMIN_TOKEN` تعریف کن؛ این توکن را داخل سورس یا APK نگذار.
7. URL Worker را در `MegaBuildConfig.ADMIN_API_URL` قرار بده و APK جدید بساز.

بعد از تنظیم `ADMIN_API_URL`، برنامه هنگام شروع و هنگام اتصال از endpoint `/api/client/subscription` لیست کانفیگ‌های فعال پنل را دریافت می‌کند و parser موجود MegaVPN آن‌ها را پردازش می‌کند.

> برای انتشار عمومی، subscription URL تستی فعلی را rotate کن و از secrets/backend استفاده کن.
