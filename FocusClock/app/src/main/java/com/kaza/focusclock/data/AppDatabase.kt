package com.kaza.focusclock.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AppDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "focus_clock.db", null, DB_VERSION) {
    companion object {
        private const val DB_VERSION = 1
        private const val ALL_DAYS = 0b1111111
        private val WEEKDAYS = 0b0011111
        private val SATURDAY = 1 shl 5
        private val SUNDAY = 1 shl 6
        private val DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

        private val BACKUP_TABLES = linkedMapOf(
            "subjects" to listOf("id", "name", "category", "color"),
            "schedule_templates" to listOf("id", "title", "subject", "start_minute", "end_minute", "days_mask", "enabled", "reminder_minutes", "counts_for_goal", "created_at", "updated_at"),
            "schedule_instances" to listOf("id", "template_id", "day_key", "status", "actual_minutes", "note", "started_at", "updated_at"),
            "task_events" to listOf("id", "template_id", "day_key", "event_type", "occurred_at", "detail"),
            "daily_logs" to listOf("id", "day_key", "content", "created_at", "updated_at"),
            "roadmap_phases" to listOf("id", "category", "title", "description", "start_date", "end_date", "sort_order"),
            "roadmap_milestones" to listOf("id", "phase_id", "title", "description", "due_date", "weight", "status", "confidence", "notes"),
            "exam_scores" to listOf("id", "subject", "score", "max_score", "exam_date", "note"),
            "habits" to listOf("id", "title", "days_mask", "enabled", "created_at"),
            "habit_checks" to listOf("id", "habit_id", "day_key", "done", "updated_at"),
            "focus_sessions" to listOf("id", "subject", "task_id", "day_key", "started_at", "duration_minutes", "completed"),
            "weekly_reports" to listOf("id", "week_key", "content", "created_at"),
            "user_settings" to listOf("key", "value")
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE subjects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, category TEXT NOT NULL DEFAULT 'OTHER', color TEXT NOT NULL DEFAULT '#858BFF')")
        db.execSQL("CREATE TABLE schedule_templates (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT NOT NULL DEFAULT '', start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, days_mask INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, reminder_minutes INTEGER NOT NULL DEFAULT 0, counts_for_goal INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE schedule_instances (id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER NOT NULL, day_key TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', actual_minutes INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', started_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, UNIQUE(template_id, day_key))")
        db.execSQL("CREATE TABLE task_events (id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER NOT NULL DEFAULT 0, day_key TEXT NOT NULL, event_type TEXT NOT NULL, occurred_at INTEGER NOT NULL, detail TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE daily_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, day_key TEXT NOT NULL UNIQUE, content TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE roadmap_phases (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', start_date TEXT NOT NULL DEFAULT '', end_date TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE roadmap_milestones (id INTEGER PRIMARY KEY AUTOINCREMENT, phase_id INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', due_date TEXT NOT NULL DEFAULT '', weight INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL DEFAULT 'NOT_STARTED', confidence INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE exam_scores (id INTEGER PRIMARY KEY AUTOINCREMENT, subject TEXT NOT NULL, score REAL NOT NULL, max_score REAL NOT NULL DEFAULT 10, exam_date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, days_mask INTEGER NOT NULL DEFAULT 127, enabled INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE habit_checks (id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER NOT NULL, day_key TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, UNIQUE(habit_id, day_key))")
        db.execSQL("CREATE TABLE focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, subject TEXT NOT NULL DEFAULT '', task_id INTEGER NOT NULL DEFAULT 0, day_key TEXT NOT NULL, started_at INTEGER NOT NULL, duration_minutes INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE weekly_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, week_key TEXT NOT NULL UNIQUE, content TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE user_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        seedDefaults(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations are additive. Keep old user data; never drop tables during an app update.
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS habit_checks (id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER NOT NULL, day_key TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, UNIQUE(habit_id, day_key))")
        }
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        listOf(
            Triple("Toán", "THPT", "#858BFF"), Triple("Vật lý", "THPT", "#49B5A6"),
            Triple("Hóa học", "THPT", "#E6A45C"), Triple("Tiếng Anh", "ENGLISH", "#6D9CFF"),
            Triple("C/C++", "TECH", "#B48AF2"), Triple("Python", "TECH", "#E5C15C"),
            Triple("ESP-IDF", "TECH", "#72B6D6"), Triple("Công nghệ", "TECH", "#B48AF2"),
            Triple("Sinh hoạt", "ROUTINE", "#7E879A"), Triple("Nghỉ", "ROUTINE", "#7E879A")
        ).forEach { (name, category, color) ->
            db.insertWithOnConflict("subjects", null, ContentValues().apply {
                put("name", name); put("category", category); put("color", color)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        fun task(title: String, subject: String, start: Int, end: Int, days: Int, enabled: Boolean = true, goal: Boolean = true) {
            db.insert("schedule_templates", null, ContentValues().apply {
                put("title", title); put("subject", subject); put("start_minute", start); put("end_minute", end)
                put("days_mask", days); put("enabled", if (enabled) 1 else 0); put("reminder_minutes", 0)
                put("counts_for_goal", if (goal) 1 else 0); put("created_at", now); put("updated_at", now)
            })
        }
        // Weekday morning lessons are intentionally disabled until the 22:00-05:00 sleep conflict is resolved.
        task("Tiếng Anh buổi sáng", "Tiếng Anh", 270, 360, WEEKDAYS, false)
        task("Học ở trường (sáng)", "Sinh hoạt", 415, 690, 1, true, false)
        task("Di chuyển về nhà", "Sinh hoạt", 690, 700, 1, true, false)
        task("Học ở trường (sáng)", "Sinh hoạt", 415, 640, WEEKDAYS and (ALL_DAYS xor 1), true, false)
        task("Di chuyển về nhà", "Sinh hoạt", 640, 650, WEEKDAYS and (ALL_DAYS xor 1), true, false)
        task("Học ở trường (chiều)", "Sinh hoạt", 800, 980, WEEKDAYS, true, false)
        task("Di chuyển về nhà", "Sinh hoạt", 980, 990, WEEKDAYS, true, false)
        task("Việc nhà, ăn tối, tắm rửa và nghỉ", "Sinh hoạt", 990, 1170, WEEKDAYS, true, false)
        task("Nghỉ giữa hai ca học", "Nghỉ", 1230, 1240, WEEKDAYS, true, false)
        task("Chuẩn bị ngày mai và thư giãn", "Nghỉ", 1290, 1320, WEEKDAYS, true, false)

        task("Toán", "Toán", 1170, 1230, 1)
        task("Bài tập Toán", "Toán", 1240, 1290, 1)
        task("Vật lý", "Vật lý", 1170, 1230, 1 shl 1)
        task("C/C++", "C/C++", 1240, 1290, 1 shl 1)
        task("Hóa học", "Hóa học", 1170, 1230, 1 shl 2)
        task("Python", "Python", 1240, 1290, 1 shl 2)
        task("Toán", "Toán", 1170, 1230, 1 shl 3)
        task("ESP-IDF", "ESP-IDF", 1240, 1290, 1 shl 3)
        task("Vật lý", "Vật lý", 1170, 1230, 1 shl 4)
        task("Hóa học", "Hóa học", 1240, 1290, 1 shl 4)

        task("Tiếng Anh", "Tiếng Anh", 300, 360, SATURDAY)
        task("Ăn sáng, nghỉ", "Nghỉ", 360, 420, SATURDAY or SUNDAY, true, false)
        task("Toán / Luyện đề Toán", "Toán", 420, 510, SATURDAY or SUNDAY)
        task("Nghỉ", "Nghỉ", 510, 525, SATURDAY or SUNDAY, true, false)
        task("Vật lý / Luyện đề Vật lý", "Vật lý", 525, 615, SATURDAY or SUNDAY)
        task("Nghỉ", "Nghỉ", 615, 630, SATURDAY or SUNDAY, true, false)
        task("Hóa học / Luyện đề Hóa học", "Hóa học", 630, 690, SATURDAY or SUNDAY)
        task("Ăn trưa, nghỉ", "Nghỉ", 690, 840, SATURDAY or SUNDAY, true, false)
        task("C/C++ hoặc ESP-IDF", "C/C++", 840, 930, SATURDAY)
        task("Linux, mạng máy tính, Cybersecurity", "Công nghệ", 840, 930, SUNDAY)
        task("Nghỉ", "Nghỉ", 930, 945, SATURDAY or SUNDAY, true, false)
        task("Bài tập trên lớp", "Toán", 945, 1005, SATURDAY)
        task("Chữa lỗi sai", "Toán", 945, 1005, SUNDAY)
        task("Việc nhà, ăn uống, nghỉ", "Sinh hoạt", 1005, 1170, SATURDAY or SUNDAY, true, false)
        task("Tiếng Anh", "Tiếng Anh", 1170, 1230, SATURDAY)
        task("Thư giãn", "Nghỉ", 1230, 1320, SATURDAY, true, false)
        task("Tiếng Anh nghe và nói", "Tiếng Anh", 300, 360, SUNDAY)
        task("Tiếng Anh", "Tiếng Anh", 1170, 1200, SUNDAY)
        task("Đánh giá tiến độ tuần", "Công nghệ", 1200, 1220, SUNDAY)
        task("Chuẩn bị tuần mới", "Sinh hoạt", 1220, 1290, SUNDAY, true, false)
        task("Thư giãn trước khi ngủ", "Nghỉ", 1290, 1320, SUNDAY, true, false)

        val phases = listOf(
            arrayOf("TECH", "Giai đoạn 1: Nền tảng", "C/C++ và Python căn bản, Git, điện tử cơ bản", "2026-11-08", "2026-11-22"),
            arrayOf("TECH", "Giai đoạn 2: Lập trình và ngoại vi", "C/C++, cấu trúc dữ liệu, GPIO, ADC, UART, ESP-IDF", "2026-11-23", "2026-12-31"),
            arrayOf("TECH", "Giai đoạn 3: IoT và mạng", "ESP32, FreeRTOS, giao thức, Python backend, Linux và TCP/IP", "2027-01-01", "2027-02-15"),
            arrayOf("TECH", "Giai đoạn 4: Bảo mật và hoàn thiện", "Bảo mật IoT, kiểm thử, AI, tài liệu và hoàn thiện dự án", "2027-02-16", "2027-03-31"),
            arrayOf("PROJECT", "Secure AIoT Monitoring System", "Dự án IoT bảo mật có dashboard và AI hỗ trợ phân tích", "2026-11-08", "2027-03-31")
        )
        phases.forEachIndexed { index, p ->
            db.insert("roadmap_phases", null, ContentValues().apply {
                put("category", p[0]); put("title", p[1]); put("description", p[2]); put("start_date", p[3]); put("end_date", p[4]); put("sort_order", index)
            })
        }
        val techPhaseIds = db.rawQuery("SELECT id FROM roadmap_phases WHERE category='TECH' ORDER BY sort_order", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        val projectPhase = db.rawQuery("SELECT id FROM roadmap_phases WHERE category='PROJECT' LIMIT 1", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        val milestones = listOf(
            techPhaseIds[0] to listOf("Đánh giá kiến thức đầu vào", "C/C++ căn bản", "Python căn bản", "Git căn bản", "Điện tử cơ bản"),
            techPhaseIds[1] to listOf("Biến, kiểu dữ liệu, hàm", "Mảng, chuỗi, con trỏ, struct", "Cấu trúc dữ liệu cơ bản", "Git thực hành", "GPIO, ADC, UART", "Làm quen ESP-IDF"),
            techPhaseIds[2] to listOf("ESP32 và FreeRTOS", "GPIO, I2C, SPI, UART", "Wi-Fi, MQTT, HTTP", "Python backend", "Linux, TCP/IP, DNS, TLS"),
            techPhaseIds[3] to listOf("Xác thực và phân quyền", "Quản lý secrets và logging", "Testing", "AI phân tích dữ liệu", "README và tài liệu kỹ thuật")
        )
        milestones.forEach { (phaseId, items) -> items.forEach { title ->
            db.insert("roadmap_milestones", null, ContentValues().apply {
                put("phase_id", phaseId); put("title", title); put("description", "Tự cập nhật nội dung và bằng chứng sau khi thực hành.")
                put("weight", 1); put("status", "NOT_STARTED"); put("confidence", 0)
            })
        } }
        listOf(
            "ESP32 thu thập dữ liệu cảm biến", "Gửi dữ liệu qua MQTT hoặc HTTPS", "Server Python nhận dữ liệu", "Lưu trữ dữ liệu",
            "Dashboard theo dõi", "Xác thực người dùng", "Bảo vệ thông tin nhạy cảm", "Logging", "AI phát hiện hoặc tóm tắt bất thường", "Kiểm thử", "README và tài liệu kỹ thuật"
        ).forEach { title -> db.insert("roadmap_milestones", null, ContentValues().apply {
            put("phase_id", projectPhase); put("title", title); put("description", "Hạng mục của Secure AIoT Monitoring System")
            put("weight", 2); put("status", "NOT_STARTED"); put("confidence", 0)
        }) }
        listOf("Toán", "Vật lý", "Hóa học").forEach { subject ->
            db.insert("roadmap_phases", null, ContentValues().apply {
                put("category", "THPT"); put("title", subject); put("description", "Mục tiêu: trên 8 điểm. Tự thêm chuyên đề theo chương trình học.")
                put("start_date", "2026-11-08"); put("end_date", ""); put("sort_order", 10)
            })
        }
        val englishPhase = db.insert("roadmap_phases", null, ContentValues().apply {
            put("category", "ENGLISH"); put("title", "English: Beginner → IELTS 5.0"); put("description", "Vocabulary, Grammar, Pronunciation, Listening, Speaking, Reading, Writing và IELTS Preparation.")
            put("start_date", "2026-11-08"); put("end_date", "2027-03-31"); put("sort_order", 20)
        })
        listOf("Vocabulary", "Grammar", "Pronunciation", "Listening", "Speaking", "Reading", "Writing", "IELTS Preparation").forEach { title ->
            db.insert("roadmap_milestones", null, ContentValues().apply {
                put("phase_id", englishPhase); put("title", title); put("description", "Cập nhật kỹ năng, số bài đã làm và kết quả kiểm tra thực tế.")
                put("weight", 1); put("status", "NOT_STARTED"); put("confidence", 0)
            })
        }
        db.rawQuery("SELECT id,title FROM roadmap_phases WHERE category='THPT'", null).use { c -> while (c.moveToNext()) {
            val subjectPhaseId = c.getLong(0)
            listOf("Chuyên đề cần học", "Bài tập cần hoàn thành", "Kiến thức còn yếu", "Luyện đề và chữa lỗi").forEach { title ->
                db.insert("roadmap_milestones", null, ContentValues().apply {
                    put("phase_id", subjectPhaseId); put("title", title); put("description", "Tự sửa tên và nội dung theo chương trình học.")
                    put("weight", 1); put("status", "NOT_STARTED"); put("confidence", 0)
                })
            }
        } }
        listOf("Học tiếng Anh", "Làm bài tập", "Ôn bài cũ", "Ghi nhật ký", "Ôn lỗi sai", "Làm dự án công nghệ", "Ngủ đúng giờ").forEach { title ->
            db.insert("habits", null, ContentValues().apply { put("title", title); put("days_mask", ALL_DAYS); put("enabled", 1); put("created_at", now) })
        }
    }

    fun getAllTemplates(): List<ScheduleTask> = readableDatabase.rawQuery(
        "SELECT * FROM schedule_templates ORDER BY start_minute", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.toTask()) } }

    fun getTemplate(id: Long): ScheduleTask? = readableDatabase.rawQuery("SELECT * FROM schedule_templates WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toTask() else null }

    fun getTasksForDate(date: LocalDate, includeDisabled: Boolean = false): List<ScheduleTask> {
        val dayMask = 1 shl (date.dayOfWeek.value - 1)
        val enabledClause = if (includeDisabled) "" else "AND t.enabled=1"
        return readableDatabase.rawQuery(
            "SELECT t.*, COALESCE(i.status,'PENDING') AS task_status, COALESCE(i.actual_minutes,0) AS task_actual " +
                "FROM schedule_templates t LEFT JOIN schedule_instances i ON i.template_id=t.id AND i.day_key=? " +
                "WHERE (t.days_mask & ?) != 0 $enabledClause ORDER BY t.start_minute, t.id",
            arrayOf(date.format(DAY_FORMAT), dayMask.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toTask(date)) } }
    }

    fun saveTemplate(task: ScheduleTask): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", task.title.trim()); put("subject", task.subject.trim()); put("start_minute", task.startMinute)
            put("end_minute", task.endMinute); put("days_mask", task.daysMask); put("enabled", if (task.enabled) 1 else 0)
            put("reminder_minutes", task.reminderMinutes); put("counts_for_goal", if (task.countsForGoal) 1 else 0); put("updated_at", now)
        }
        if (task.id > 0) return writableDatabase.update("schedule_templates", values, "id=?", arrayOf(task.id.toString())).let { task.id }
        values.put("created_at", now)
        return writableDatabase.insert("schedule_templates", null, values)
    }

    fun deleteTemplate(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("schedule_instances", "template_id=?", arrayOf(id.toString()))
            writableDatabase.delete("task_events", "template_id=?", arrayOf(id.toString()))
            writableDatabase.delete("schedule_templates", "id=?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun setTemplateEnabled(id: Long, enabled: Boolean) {
        writableDatabase.update("schedule_templates", ContentValues().apply { put("enabled", if (enabled) 1 else 0); put("updated_at", System.currentTimeMillis()) }, "id=?", arrayOf(id.toString()))
    }

    fun markTask(id: Long, date: LocalDate, status: String, note: String = "", actualMinutes: Int? = null) {
        val task = getTemplate(id) ?: return
        val now = System.currentTimeMillis()
        val actual = actualMinutes ?: when (status) {
            "COMPLETED" -> task.durationMinutes
            "PARTIAL" -> task.durationMinutes / 2
            else -> 0
        }
        val db = writableDatabase
        db.insertWithOnConflict("schedule_instances", null, ContentValues().apply {
            put("template_id", id); put("day_key", date.format(DAY_FORMAT)); put("status", status)
            put("actual_minutes", actual.coerceIn(0, task.durationMinutes)); put("note", note); put("updated_at", now)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.insert("task_events", null, ContentValues().apply {
            put("template_id", id); put("day_key", date.format(DAY_FORMAT)); put("event_type", status)
            put("occurred_at", now); put("detail", note)
        })
    }

    fun logTaskEvent(id: Long, date: LocalDate, type: String, detail: String = "") {
        writableDatabase.insert("task_events", null, ContentValues().apply {
            put("template_id", id); put("day_key", date.format(DAY_FORMAT)); put("event_type", type)
            put("occurred_at", System.currentTimeMillis()); put("detail", detail)
        })
    }

    fun saveDailyNote(date: LocalDate, content: String) {
        val now = System.currentTimeMillis()
        writableDatabase.insertWithOnConflict("daily_logs", null, ContentValues().apply {
            put("day_key", date.format(DAY_FORMAT)); put("content", content); put("created_at", now); put("updated_at", now)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.insert("task_events", null, ContentValues().apply {
            put("template_id", 0); put("day_key", date.format(DAY_FORMAT)); put("event_type", "NOTE_SAVED"); put("occurred_at", now); put("detail", "Đã cập nhật nhật ký")
        })
    }

    fun getDailyNote(date: LocalDate): String = readableDatabase.rawQuery("SELECT content FROM daily_logs WHERE day_key=?", arrayOf(date.format(DAY_FORMAT))).use { c -> if (c.moveToFirst()) c.getString(0) else "" }

    fun getJournalLines(date: LocalDate): List<String> {
        val day = date.format(DAY_FORMAT)
        val lines = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT e.event_type,e.occurred_at,e.detail,t.title FROM task_events e LEFT JOIN schedule_templates t ON t.id=e.template_id WHERE e.day_key=? ORDER BY e.occurred_at DESC",
            arrayOf(day)
        ).use { c -> while (c.moveToNext()) {
            val type = c.getString(0)
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(c.getLong(1)))
            val title = c.getString(3).orEmpty().ifBlank { "Nhật ký" }
            val detail = c.getString(2).orEmpty()
            val label = when (type) {
                "COMPLETED" -> "Hoàn thành"
                "PARTIAL" -> "Hoàn thành một phần"
                "MISSED" -> "Bỏ lỡ"
                "STARTED" -> "Bắt đầu"
                "SNOOZED" -> "Đã hoãn"
                "SKIPPED" -> "Đã bỏ qua"
                "NOTE_SAVED" -> "Ghi chú"
                else -> type
            }
            lines.add("$time · $label · $title${if (detail.isNotBlank()) " · $detail" else ""}")
        } }
        return lines
    }

    fun getDayStats(date: LocalDate): DayStats {
        val tasks = getTasksForDate(date).filter { it.countsForGoal }
        val planned = tasks.sumOf { it.durationMinutes }
        val completed = tasks.sumOf { it.actualMinutes }
        val focus = readableDatabase.rawQuery("SELECT COALESCE(SUM(duration_minutes),0) FROM focus_sessions WHERE day_key=?", arrayOf(date.format(DAY_FORMAT))).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        return DayStats(planned, completed, focus, tasks.count { it.status == "COMPLETED" }, tasks.count { it.status == "PARTIAL" }, tasks.count { it.status == "MISSED" })
    }

    fun addFocusSession(subject: String, taskId: Long, date: LocalDate, startedAt: Long, durationMinutes: Int, completed: Boolean) {
        if (durationMinutes <= 0) return
        writableDatabase.insert("focus_sessions", null, ContentValues().apply {
            put("subject", subject); put("task_id", taskId); put("day_key", date.format(DAY_FORMAT)); put("started_at", startedAt)
            put("duration_minutes", durationMinutes); put("completed", if (completed) 1 else 0)
        })
        writableDatabase.insert("task_events", null, ContentValues().apply {
            put("template_id", taskId); put("day_key", date.format(DAY_FORMAT)); put("event_type", "FOCUS_SESSION")
            put("occurred_at", System.currentTimeMillis()); put("detail", "$durationMinutes phút · $subject")
        })
    }

    fun getWeeklyFocusBySubject(date: LocalDate): List<Pair<String, Int>> {
        val start = date.minusDays((date.dayOfWeek.value - 1).toLong()).format(DAY_FORMAT)
        val end = date.plusDays((7 - date.dayOfWeek.value).toLong()).format(DAY_FORMAT)
        return readableDatabase.rawQuery("SELECT subject,SUM(duration_minutes) FROM focus_sessions WHERE day_key BETWEEN ? AND ? GROUP BY subject ORDER BY SUM(duration_minutes) DESC", arrayOf(start, end)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) }
        }
    }

    fun getMilestones(): List<RoadmapMilestone> = readableDatabase.rawQuery(
        "SELECT m.id,p.category,p.title,m.title,m.description,m.due_date,m.weight,m.status,m.confidence,m.notes FROM roadmap_milestones m JOIN roadmap_phases p ON p.id=m.phase_id ORDER BY p.sort_order,p.id,m.id", null
    ).use { c -> buildList { while (c.moveToNext()) add(RoadmapMilestone(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getInt(6), c.getString(7), c.getInt(8), c.getString(9))) } }

    fun updateMilestone(id: Long, status: String, confidence: Int, notes: String) {
        writableDatabase.update("roadmap_milestones", ContentValues().apply {
            put("status", status); put("confidence", confidence.coerceIn(0, 100)); put("notes", notes)
        }, "id=?", arrayOf(id.toString()))
        writableDatabase.insert("task_events", null, ContentValues().apply {
            put("template_id", 0); put("day_key", LocalDate.now().format(DAY_FORMAT)); put("event_type", "MILESTONE_UPDATED")
            put("occurred_at", System.currentTimeMillis()); put("detail", "Đã cập nhật milestone #$id: $status")
        })
    }

    fun addMilestone(category: String, phaseTitle: String, title: String, dueDate: String) {
        val db = writableDatabase
        var phaseId = 0L
        db.rawQuery("SELECT id FROM roadmap_phases WHERE category=? AND title=? LIMIT 1", arrayOf(category, phaseTitle)).use { c -> if (c.moveToFirst()) phaseId = c.getLong(0) }
        if (phaseId == 0L) phaseId = db.insert("roadmap_phases", null, ContentValues().apply {
            put("category", category); put("title", phaseTitle); put("description", "Mục tiêu do người dùng thêm"); put("sort_order", 99)
        })
        db.insert("roadmap_milestones", null, ContentValues().apply {
            put("phase_id", phaseId); put("title", title); put("due_date", dueDate); put("weight", 1); put("status", "NOT_STARTED"); put("confidence", 0)
        })
    }

    fun getHabitsForDate(date: LocalDate): List<Triple<Long, String, Boolean>> {
        val dayMask = 1 shl (date.dayOfWeek.value - 1)
        return readableDatabase.rawQuery(
            "SELECT h.id,h.title,COALESCE(c.done,0) FROM habits h LEFT JOIN habit_checks c ON c.habit_id=h.id AND c.day_key=? WHERE h.enabled=1 AND (h.days_mask & ?) != 0 ORDER BY h.id",
            arrayOf(date.format(DAY_FORMAT), dayMask.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getInt(2) == 1)) } }
    }

    fun setHabitDone(habitId: Long, date: LocalDate, done: Boolean) {
        writableDatabase.insertWithOnConflict("habit_checks", null, ContentValues().apply {
            put("habit_id", habitId); put("day_key", date.format(DAY_FORMAT)); put("done", if (done) 1 else 0); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.insert("task_events", null, ContentValues().apply {
            put("template_id", 0); put("day_key", date.format(DAY_FORMAT)); put("event_type", "HABIT_UPDATED")
            put("occurred_at", System.currentTimeMillis()); put("detail", "${if (done) "Hoàn thành" else "Bỏ đánh dấu"} thói quen #$habitId")
        })
    }

    fun addExamScore(subject: String, score: Double, date: LocalDate, note: String) {
        writableDatabase.insert("exam_scores", null, ContentValues().apply {
            put("subject", subject); put("score", score); put("max_score", 10.0); put("exam_date", date.format(DAY_FORMAT)); put("note", note)
        })
    }

    fun getScoreSummary(): List<Pair<String, Double>> = readableDatabase.rawQuery(
        "SELECT subject,AVG(score) FROM exam_scores GROUP BY subject ORDER BY subject", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getDouble(1)) } }

    fun getRoadmapCompletion(category: String? = null): Int {
        val sql = if (category == null) "SELECT COALESCE(SUM(CASE WHEN status='DONE' THEN weight ELSE 0 END),0),COALESCE(SUM(weight),0) FROM roadmap_milestones" else "SELECT COALESCE(SUM(CASE WHEN m.status='DONE' THEN m.weight ELSE 0 END),0),COALESCE(SUM(m.weight),0) FROM roadmap_milestones m JOIN roadmap_phases p ON p.id=m.phase_id WHERE p.category=?"
        return readableDatabase.rawQuery(sql, if (category == null) null else arrayOf(category)).use { c ->
            if (c.moveToFirst() && c.getInt(1) > 0) c.getInt(0) * 100 / c.getInt(1) else 0
        }
    }

    fun getMilestoneCounts(): Map<String, Int> = readableDatabase.rawQuery("SELECT status,COUNT(*) FROM roadmap_milestones GROUP BY status", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
    }

    fun saveSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict("user_settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun exportJson(settingsStore: SettingsStore? = null): String {
        val root = JSONObject().put("app", "FocusClock").put("schema_version", DB_VERSION).put("exported_at", System.currentTimeMillis())
        if (settingsStore != null) root.put("preferences", settingsStore.toJson())
        BACKUP_TABLES.forEach { (table, columns) ->
            val rows = JSONArray()
            readableDatabase.rawQuery("SELECT ${columns.joinToString(",")} FROM $table", null).use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    columns.forEachIndexed { i, column ->
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(column, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(column, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(column, c.getDouble(i))
                            else -> row.put(column, c.getString(i))
                        }
                    }
                    rows.put(row)
                }
            }
            root.put(table, rows)
        }
        return root.toString(2)
    }

    fun importJson(json: String, settingsStore: SettingsStore? = null) {
        val root = JSONObject(json)
        require(root.optString("app") == "FocusClock") { "Tệp không phải bản backup Focus Clock." }
        require(root.optInt("schema_version") == DB_VERSION) { "Phiên bản backup không tương thích." }
        // Validate the whole backup before deleting any existing rows. A partial or
        // malformed file must never turn a failed restore into silent data loss.
        BACKUP_TABLES.keys.forEach { table ->
            require(root.optJSONArray(table) != null) { "Bản backup thiếu dữ liệu bảng $table." }
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("task_events", "schedule_instances", "focus_sessions", "habit_checks", "daily_logs", "roadmap_milestones", "roadmap_phases", "exam_scores", "habits", "schedule_templates", "subjects", "weekly_reports", "user_settings").forEach { db.delete(it, null, null) }
            BACKUP_TABLES.forEach { (table, columns) ->
                val rows = root.optJSONArray(table) ?: JSONArray()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val values = ContentValues()
                    columns.forEach { column ->
                        if (row.has(column) && !row.isNull(column)) {
                            when (val value = row.get(column)) {
                                is Int -> values.put(column, value)
                                is Long -> values.put(column, value)
                                is Double -> values.put(column, value)
                                is Boolean -> values.put(column, if (value) 1 else 0)
                                else -> values.put(column, value.toString())
                            }
                        }
                    }
                    db.insertOrThrow(table, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        root.optJSONObject("preferences")?.let { settingsStore?.restoreFromJson(it) }
    }

    fun exportJournalCsv(): String {
        val out = StringBuilder("date,time,type,detail\n")
        val query = "SELECT day_key,occurred_at,event_type,COALESCE(t.title,'') || CASE WHEN detail='' THEN '' ELSE ' · ' || detail END AS detail " +
            "FROM task_events e LEFT JOIN schedule_templates t ON t.id=e.template_id " +
            "UNION ALL SELECT day_key,updated_at,'DAILY_NOTE',content FROM daily_logs ORDER BY day_key,2"
        readableDatabase.rawQuery(query, null).use { c -> while (c.moveToNext()) {
            val day = c.getString(0)
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(c.getLong(1)))
            val type = c.getString(2)
            val detail = c.getString(3).orEmpty()
            fun csv(value: String) = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""
            out.append(csv(day)).append(',').append(csv(time)).append(',').append(csv(type)).append(',').append(csv(detail)).append('\n')
        } }
        return out.toString()
    }

    fun getTasksDueInRange(start: LocalDate, days: Int): List<Pair<LocalDate, ScheduleTask>> {
        val out = mutableListOf<Pair<LocalDate, ScheduleTask>>()
        for (offset in 0 until days) {
            val date = start.plusDays(offset.toLong())
            getTasksForDate(date).filter { it.countsForGoal && it.status == "PENDING" }.forEach { out.add(date to it) }
        }
        return out
    }

    private fun Cursor.toTask(date: LocalDate? = null): ScheduleTask {
        val statusIndex = getColumnIndex("task_status")
        val actualIndex = getColumnIndex("task_actual")
        return ScheduleTask(
            id = getLong(getColumnIndexOrThrow("id")), title = getString(getColumnIndexOrThrow("title")),
            subject = getString(getColumnIndexOrThrow("subject")), startMinute = getInt(getColumnIndexOrThrow("start_minute")),
            endMinute = getInt(getColumnIndexOrThrow("end_minute")), daysMask = getInt(getColumnIndexOrThrow("days_mask")),
            enabled = getInt(getColumnIndexOrThrow("enabled")) == 1, reminderMinutes = getInt(getColumnIndexOrThrow("reminder_minutes")),
            countsForGoal = getInt(getColumnIndexOrThrow("counts_for_goal")) == 1, date = date,
            status = if (statusIndex >= 0) getString(statusIndex) else "PENDING",
            actualMinutes = if (actualIndex >= 0) getInt(actualIndex) else 0
        )
    }
}
