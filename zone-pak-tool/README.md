# ZONE PAK TOOL v0.2

ابزار خط فرمان برای Termux/Android جهت کار با Unreal Engine PAK، با رابط منویی مشابه ابزار نمونه.

## نصب در Termux
```bash
pkg update
pkg install python git
cd ~
git clone -b zone-pak-tool-v0.2 https://github.com/aboliihjvb/megaVPN-Android.git
cd megaVPN-Android/zone-pak-tool
python zone_tool.py
```

## قابلیت‌ها
- UNPACK PAK
- INJECT / EDIT (آماده‌سازی پوشه برای ویرایش)
- REPACK FULL
- REPACK TO PATH
- BUILD NEW PAK
- DELETE FOLDER با تأیید
- SETTINGS برای ثبت مسیر UnrealPak
- نمایش خروجی و خطای واقعی UnrealPak
- بکاپ خودکار هنگام جایگزینی PAK موجود
- ثبت لاگ در `~/.zone_pak_tool/zone_tool.log`

## نکته مهم
این ابزار خودش parser کامل PAK نیست و برای Extract/Create به یک **UnrealPak سازگار با نسخه PAK بازی** نیاز دارد. UnrealPak را از منبع/SDK مجاز پروژه یا بازی خودت تهیه کن و مسیر آن را از گزینه Settings ثبت کن.

گزینه Protect عمداً عملیات رمزگذاری، خراب‌کردن فایل یا دورزدن محافظت بازی را انجام نمی‌دهد.
