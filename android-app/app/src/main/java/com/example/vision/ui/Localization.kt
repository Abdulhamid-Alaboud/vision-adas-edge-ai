package com.example.vision.ui

/**
 * Clean, lightweight bilingual localization strings for the VISION ADAS app.
 * Complete 100% Arabic and English parity across all screens.
 */
object AppStrings {

    fun get(key: String, isArabic: Boolean): String {
        val entry = STRINGS[key] ?: return key
        return if (isArabic) entry.ar else entry.en
    }

    private data class Loc(val ar: String, val en: String)

    private val STRINGS = mapOf(
        // General / Common
        "app_name" to Loc("VISION", "VISION"),
        "back" to Loc("رجوع", "Back"),
        "cancel" to Loc("إلغاء", "Cancel"),
        "close" to Loc("إغلاق", "Close"),
        "save" to Loc("حفظ", "Save"),
        "confirm" to Loc("تأكيد", "Confirm"),
        "online" to Loc("متصل", "Connected"),
        "offline" to Loc("غير متصل", "Disconnected"),
        "stopped" to Loc("متوقف", "Stopped"),
        "ms" to Loc("مللي ثانية", "ms"),
        "inference" to Loc("استدلال", "Inf"),

        // Language Switcher
        "select_language" to Loc("اختر اللغة / Language", "Select Language / اختر اللغة"),
        "lang_ar" to Loc("العربية", "Arabic"),
        "lang_en" to Loc("English", "English"),
        "language_section_title" to Loc("لغة النظام والتنبيهات الصوتية", "Language & Voice Alerts"),
        "language_section_subtitle" to Loc("اختر لغة الواجهات وأصوات التحذير بالسيارة", "Choose app interface and in-vehicle voice alerts"),

        // Auth Screen
        "auth_app_title" to Loc("نظام الرؤية المتقدم للقيادة الآمنة", "Advanced Driver Assistant System"),
        "auth_app_subtitle" to Loc("الذكاء الاصطناعي لحماية الطريق والوقاية من الحوادث", "Edge AI for Road Safety & Hazard Prevention"),
        "tab_login" to Loc("تسجيل الدخول", "Sign In"),
        "tab_register" to Loc("تسجيل سائق جديد", "Register Driver"),
        "username" to Loc("اسم المستخدم", "Username"),
        "password" to Loc("كلمة المرور", "Password"),
        "confirm_password" to Loc("تأكيد كلمة المرور", "Confirm Password"),
        "btn_sign_in" to Loc("دخول إلى النظام", "Sign In"),
        "btn_register" to Loc("إنشاء الحساب وبدء القيادة", "Create Account & Start Driving"),
        "btn_guest" to Loc("المتابعة كـ ضيف (بدون حساب)", "Continue as Guest"),
        "link_forgot_password" to Loc("نسيت كلمة المرور؟", "Forgot Password?"),
        "dialog_reset_title" to Loc("استعادة كلمة المرور", "Reset Password"),
        "dialog_reset_msg" to Loc("أدخل اسم المستخدم لتغيير كلمة المرور", "Enter your username to set a new password"),
        "new_password" to Loc("كلمة المرور الجديدة", "New Password"),
        "btn_save_password" to Loc("حفظ كلمة المرور الجديدة", "Save New Password"),
        "err_enter_username" to Loc("يرجى إدخال اسم المستخدم", "Please enter your username"),
        "err_enter_password" to Loc("يرجى إدخال كلمة المرور", "Please enter your password"),
        "err_passwords_mismatch" to Loc("كلمتا المرور غير متطابقتين!", "Passwords do not match!"),
        "err_user_taken" to Loc("اسم المستخدم مسجل مسبقاً، يرجى اختيار اسم آخر", "Username is already taken, please choose another"),
        "err_invalid_creds" to Loc("بيانات الدخول غير صحيحة، يرجى المحاولة ثانية", "Invalid credentials, please try again"),
        "err_user_not_found" to Loc("لم يتم العثور على هذا المستخدم في النظام!", "User not found in the system!"),
        "msg_account_created" to Loc("تم إنشاء حساب السائق بنجاح! يمكنك الدخول الآن", "Account created successfully! You can sign in now"),
        "msg_password_reset" to Loc("تم تحديث كلمة المرور بنجاح! سجل الدخول بالكلمة الجديدة", "Password reset successfully! Sign in with your new password"),

        // Dashboard Screen
        "start_monitoring" to Loc("بدء المراقبة", "Start Monitoring"),
        "stop_monitoring" to Loc("إيقاف المراقبة", "Stop Monitoring"),
        "hazard_alert" to Loc("تنبيه خطر!", "Hazard Alert!"),
        "camera_zoom" to Loc("التقريب", "Zoom"),
        "monitoring_prompt" to Loc("اضغط أدناه لبدء المراقبة الذكية للطريق", "Press below to start smart road monitoring"),
        "desc_settings" to Loc("الإعدادات", "Settings"),
        "desc_history" to Loc("السجل", "History"),
        "desc_analytics" to Loc("التحليلات", "Analytics"),
        "desc_admin" to Loc("المشرف", "Admin"),

        // Settings Screen
        "settings_title" to Loc("إعدادات النظام وتخصيص التنبيهات", "System Settings & Preferences"),
        "alert_volume" to Loc("مستوى صوت التنبيهات", "Alert Volume"),
        "detection_sensitivity" to Loc("حساسية الكشف ودقة الرصد", "Detection Sensitivity"),
        "detection_sensitivity_desc" to Loc("النسبة القياسية 38% تضمن التوازن بين الرصد المبكر ومنع الإنذارات الخاطئة.", "Standard 38% threshold guarantees early warnings with minimal false alarms."),
        "hazards_toggle_title" to Loc("تفعيل وإيقاف فئات المخاطر (20 فئة)", "Hazard Categories (20 Classes)"),
        "group_pedestrians" to Loc("المشاة وممرات العبور", "Pedestrians & Crossings"),
        "group_critical" to Loc("المطبات والإشارات واللوحات الحرجة", "Speed Bumps, Signals & Critical Signs"),
        "group_speed" to Loc("لوحات السرعة المحددة", "Speed Limit Signs"),
        "btn_save_prefs" to Loc("حفظ الإعدادات", "Save Preferences"),
        "msg_prefs_saved" to Loc("تم حفظ التفضيلات بنجاح!", "Preferences saved successfully!"),
        "btn_logout" to Loc("تسجيل الخروج من الحساب", "Sign Out of Account"),
        "dialog_logout_title" to Loc("تأكيد تسجيل الخروج", "Confirm Sign Out"),
        "dialog_logout_msg" to Loc("هل أنت متأكد من رغبتك في تسجيل الخروج وإنهاء جلسة القيادة؟", "Are you sure you want to sign out and end your driving session?"),
        "btn_logout_confirm" to Loc("تسجيل الخروج", "Sign Out"),

        // History Screen
        "history_screen_title" to Loc("سجل الرحلات والمخاطر المكتشفة", "Trip & Hazard History"),
        "filter_all" to Loc("الكل", "All"),
        "filter_prohibitory" to Loc("لوحات المنع والتوقف", "Prohibitory & Stop Signs"),
        "filter_speed" to Loc("لوحات السرعة", "Speed Limits"),
        "filter_pedestrians" to Loc("المشاة وممراتهم", "Pedestrians & Crossings"),
        "filter_bumps" to Loc("المطبات الصناعية", "Speed Bumps"),
        "filter_signals" to Loc("إشارات المرور", "Traffic Signals"),
        "filter_gravel" to Loc("حصى متطاير", "Loose Gravel"),
        "no_hazards_filtered" to Loc("لا توجد مخاطر مسجلة في هذا القسم", "No hazards recorded in this category"),
        "driver_label" to Loc("السائق", "Driver"),
        "location_coords" to Loc("إحداثيات الموقع", "Coordinates"),
        "gps_unavailable" to Loc("إشارة GPS غير متاحة", "GPS unavailable"),
        "btn_export_csv" to Loc("تصدير سجل الرحلات (CSV)", "Export Trip Log (CSV)"),
        "btn_share_history" to Loc("مشاركة سجل الرحلات", "Share Trip Log"),
        "toast_export_saved" to Loc("تم حفظ سجل الرحلات في التنزيلات:", "Trip log saved to Downloads:"),
        "toast_export_failed" to Loc("تعذر تصدير سجل الرحلات", "Failed to export trip log"),

        // Analytics Screen
        "analytics_screen_title" to Loc("تحليلات السلامة المرورية", "Traffic Safety Analytics"),
        "tab_latest_trip" to Loc("الرحلة الأخيرة", "Latest Trip"),
        "tab_all_trips" to Loc("كافة الرحلات", "All Trips"),
        "metric_safety_index" to Loc("مؤشر السلامة", "Safety Index"),
        "metric_total_trips" to Loc("إجمالي الرحلات", "Total Trips"),
        "metric_total_hazards" to Loc("المخاطر المرصودة", "Hazards Detected"),
        "metric_top_hazard" to Loc("الخطر الأكثر تكراراً", "Most Frequent Hazard"),
        "metric_avg_per_trip" to Loc("معدل المخاطر / رحلة", "Avg Hazards / Trip"),
        "completed_trips" to Loc("الرحلات المكتملة", "Completed Trips"),
        "hazard_breakdown_title" to Loc("توزيع المخاطر المصادفة", "Hazard Distribution Breakdown"),
        "no_hazards" to Loc("لا توجد مخاطر مسجلة", "No hazards recorded"),
        "dialog_clear_title" to Loc("تأكيد تصفير السجل", "Confirm Reset History"),
        "dialog_clear_msg" to Loc("هل أنت متأكد من رغبتك في مسح كافة سجلات الرحلات والإحصائيات القديمة من هذا الهاتف؟ سيساعدك هذا في بدء إحصائيات نظيفة ودقيقة بنسبة 100%.", "Are you sure you want to clear all trip history and statistics from this device? This will reset telemetry for clean performance tracking."),
        "btn_confirm_clear" to Loc("نعم، تصفير السجل", "Yes, Reset History"),
        "toast_history_cleared" to Loc("تم مسح وتصفير السجل بنجاح!", "History reset successfully!"),
        "safety_status_excellent" to Loc("ممتاز - قيادة نموذجية خالية من المخاطر الحرجة", "Excellent - Ideal driving with low critical hazards"),
        "safety_status_good" to Loc("جيد - مخاطر منخفضة أثناء القيادة", "Good - Low hazard rate during driving"),
        "safety_status_attention" to Loc("انتباه - تكرار رصد مطبات أو مشاة بكثرة", "Attention - Frequent bumps or pedestrians detected"),
        "safety_status_caution" to Loc("تحذير - قيادة في مناطق عالية الكثافة والمخاطر", "Caution - High risk density area driving"),

        // Admin Screen
        "admin_screen_title" to Loc("لوحة تحكم المشرف والمطور", "Admin & Developer Dashboard"),
        "admin_screen_subtitle" to Loc("إحصائيات الذكاء الاصطناعي وإدارة النظام", "AI Model Diagnostics & System Management"),
        "system_overview" to Loc("نظرة عامة على النظام", "System Overview"),
        "total_trips_stat" to Loc("إجمالي الرحلات", "Total Trips"),
        "total_detections_stat" to Loc("إجمالي الرصد", "Total Detections"),
        "registered_drivers" to Loc("السائقين", "Drivers"),
        "export_cloud_section" to Loc("التصدير والمزامنة السحابية", "Export & Cloud Sync"),
        "btn_export_txt" to Loc("مشاركة التقرير المحلي (TXT)", "Share Local Report (TXT)"),
        "btn_export_cloud" to Loc("مشاركة التقرير السحابي الموحد", "Share Cloud Fleet Report (TXT)"),
        "btn_copy_report" to Loc("نسخ التقرير إلى الحافظة", "Copy Report to Clipboard"),
        "btn_clear_history" to Loc("تصفير ومسح السجل بالكامل", "Reset Complete History"),
        "toast_report_copied" to Loc("تم نسخ تقرير الأداء إلى الحافظة بنجاح!", "Performance report copied to clipboard!"),
        "toast_no_data" to Loc("لا توجد بيانات متاحة للتصدير حتى الآن", "No data available for export yet"),
        "preview_report_title" to Loc("معاينة التقرير الفني", "Technical Report Preview"),
        "fleet_title" to Loc("أسطول السائقين والنشاط السحابي", "Driver Fleet & Cloud Activity"),
        "no_drivers_found" to Loc("لا توجد سجلات سائقين حالياً", "No driver records found"),
        "driver_item_trips" to Loc("رحلة", "trips"),
        "driver_item_hazards" to Loc("مخاطر", "hazards"),
        "hazards_catalog_title" to Loc("سجل رصد الفئات المرورية (20 فئة)", "Traffic Categories Log (20 Classes)"),
        "detections_count" to Loc("رصد", "detections"),
        "ai_sensitivity_title" to Loc("مصفوفة دقة وتخصيص الفئات (Per-Class Confidence Matrix)", "Per-Class Confidence Calibration Matrix"),
        "ai_sensitivity_desc" to Loc("توزيع نسب الثقة الديناميكية الذكية لكل فئة مرورية:", "Dynamic confidence thresholds calibrated per category:"),
        "recommended_range" to Loc("(معايرة تلقائية من نتائج التدريب)", "(Auto-calibrated from training Pareto audit)"),
        "model_specs_title" to Loc("معلومات النظام والنموذج (System & Model Specs)", "System & Model Architecture Specs"),
        "spec_model_name" to Loc("• النموذج: YOLO11n-ADAS-v2 (رؤية حاسوبية فورية لمخاطر الطرق)", "• Model: YOLO11n-ADAS-v2 (Real-Time Road Hazard ADAS)"),
        "spec_architecture" to Loc("• المعمارية: C3k2 + C2PSA + Detect Head (CNN فائق السرعة)", "• Architecture: C3k2 + C2PSA + Detect Head (Fast Mobile CNN)"),
        "spec_params" to Loc("• البارامترات والتعقيد: 2.58M Params (6.4 GFLOPs - فائق الخفة)", "• Complexity: 2.58M Params (6.4 GFLOPs - Ultra Lightweight)"),
        "spec_input" to Loc("• أبعاد الإدخال: 640×640 (RGB Planar Float32)", "• Input Dimension: 640×640 (RGB Planar Float32)"),
        "spec_output" to Loc("• مصفوفة الإخراج: [1, 18, 8400] (صناديق رصد ديناميكية سريعة)", "• Output Grid: [1, 18, 8400] Dynamic Anchor Detections"),
        "spec_classes" to Loc("• التدريب والمقاييس: 150 جولة • الدقة 98.1% • mAP@50 96.3%", "• Training & Metrics: 150 Epochs • P 98.1% • mAP@50 96.3%"),
        "spec_storage" to Loc("• التخزين: قاعدة بيانات SQLite محلية آمنة (حسابات السائقين)", "• Storage: Secure Local SQLite Database (Registered Drivers)"),
        "cloud_status" to Loc("حالة المزامنة السحابية", "Cloud Sync Status")
    )
}
