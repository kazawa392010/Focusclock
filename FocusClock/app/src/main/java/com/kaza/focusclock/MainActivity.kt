package com.kaza.focusclock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.kaza.focusclock.data.AppDatabase
import com.kaza.focusclock.data.RoadmapMilestone
import com.kaza.focusclock.data.ScheduleTask
import com.kaza.focusclock.data.SettingsStore
import com.kaza.focusclock.reminders.ReminderScheduler
import com.kaza.focusclock.ui.UiKit
import com.kaza.focusclock.util.TimeUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_NOTIFICATIONS = 4001
        private const val REQUEST_EXPORT_JSON = 4101
        private const val REQUEST_IMPORT_JSON = 4102
        private const val REQUEST_EXPORT_CSV = 4103
    }

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private lateinit var ui: UiKit
    private var page = "HOME"
    private var selectedDate = LocalDate.now()
    private var clockView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var focusTimer: CountDownTimer? = null
    private var timerStartedAt = 0L
    private var timerRemainingMs = 0L
    private var timerTotalMs = 0L
    private var timerBreakMinutes = 0
    private var timerRunning = false
    private var timerSubject = "Tập trung"
    private var timerTaskId = 0L
    private var timerDialog: AlertDialog? = null
    private val clockTick = object : Runnable {
        override fun run() {
            clockView?.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            if (page == "HOME") handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase(this)
        settings = SettingsStore(this)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        content = findViewById(R.id.content)
        bottomNav = findViewById(R.id.bottom_nav)
        findViewById<TextView>(R.id.settings_button).setOnClickListener { navigate("SETTINGS") }
        applyTheme()
        buildBottomNav()
        renderPage()
        if (!settings.conflictShown) showInitialConflict()
        requestNotificationPermissionIfNeeded()
        ReminderScheduler.rescheduleAll(this)
    }

    override fun onResume() {
        super.onResume()
        if (page == "HOME") handler.post(clockTick)
        if (page == "SETTINGS") renderPage()
        handleNotificationIntent(intent)
    }

    override fun onPause() {
        handler.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTick)
        focusTimer?.cancel()
        if (timerTotalMs > 0) {
            val elapsed = ((timerTotalMs - timerRemainingMs).coerceAtLeast(0L) / 60_000L).toInt()
            if (elapsed > 0) db.addFocusSession(timerSubject, timerTaskId, LocalDate.now(), timerStartedAt, elapsed, false)
            timerTotalMs = 0
        }
        db.close()
        super.onDestroy()
    }

    @Deprecated("Use the system back dispatcher on newer Android versions")
    override fun onBackPressed() {
        if (page != "HOME") navigate("HOME") else super.onBackPressed()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_journal", false) == true) {
            selectedDate = runCatching { LocalDate.parse(intent.getStringExtra("open_date")) }.getOrNull() ?: LocalDate.now()
            page = "JOURNAL"
            buildBottomNav()
            renderPage()
            intent.removeExtra("open_journal")
            return
        }
        val taskId = intent?.getLongExtra("open_task_id", 0L) ?: 0L
        if (taskId > 0) {
            val date = runCatching { LocalDate.parse(intent?.getStringExtra("open_date")) }.getOrNull() ?: LocalDate.now()
            page = "SCHEDULE"
            selectedDate = date
            buildBottomNav()
            renderPage()
            db.getTemplate(taskId)?.let { showTaskActions(it.copy(date = date, status = "PENDING")) }
            intent?.removeExtra("open_task_id")
        }
    }

    private fun applyTheme() {
        val dark = settings.darkMode
        val bg = Color.parseColor(if (dark) "#11131A" else "#F3F5F9")
        val surface = Color.parseColor(if (dark) "#191C26" else "#FFFFFF")
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        ui = UiKit(this, dark, runCatching { Color.parseColor(settings.accent) }.getOrDefault(Color.parseColor("#858BFF")))
        root.setBackgroundColor(ui.background)
        bottomNav.setBackgroundColor(ui.surface)
        findViewById<LinearLayout>(R.id.top_bar)?.setBackgroundColor(ui.surface)
        findViewById<TextView>(R.id.brand)?.setTextColor(ui.text)
        findViewById<TextView>(R.id.settings_button)?.setTextColor(ui.text)
    }

    private fun buildBottomNav() {
        bottomNav.removeAllViews()
        val items = listOf(
            Triple("HOME", "◷", "Home"), Triple("SCHEDULE", "▦", "Lịch"),
            Triple("ROADMAP", "⌁", "Lộ trình"), Triple("JOURNAL", "▤", "Nhật ký"),
            Triple("ANALYTICS", "◔", "Thống kê")
        )
        items.forEach { (key, icon, label) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(6), 0, ui.dp(5))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { navigate(key) }
            }
            val color = if (page == key) ui.accent else ui.muted
            cell.addView(ui.text(icon, 20f, color, true))
            cell.addView(ui.text(label, 10f, color, page == key))
            bottomNav.addView(cell)
        }
    }

    private fun navigate(destination: String) {
        page = destination
        handler.removeCallbacks(clockTick)
        buildBottomNav()
        renderPage()
        if (page == "HOME") handler.post(clockTick)
    }

    private fun renderPage() {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val column = ui.column(18)
        column.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(26))
        scroll.addView(column)
        content.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        when (page) {
            "HOME" -> renderHome(column)
            "SCHEDULE" -> renderSchedule(column)
            "ROADMAP" -> renderRoadmap(column)
            "JOURNAL" -> renderJournal(column)
            "ANALYTICS" -> renderAnalytics(column)
            "SETTINGS" -> renderSettings(column)
        }
    }

    private fun heading(parent: LinearLayout, title: String, subtitle: String? = null) {
        parent.addView(ui.text(title, 23f, ui.text, true))
        if (!subtitle.isNullOrBlank()) parent.addView(ui.text(subtitle, 12f, ui.muted))
        ui.addSpace(parent, 16)
    }

    private fun sectionLabel(parent: LinearLayout, title: String) {
        parent.addView(ui.text(title.uppercase(Locale.getDefault()), 11f, ui.muted, true))
        ui.addSpace(parent, 8)
    }

    private fun addCard(parent: LinearLayout, title: String? = null, body: String? = null, accent: Int? = null): LinearLayout {
        val card = ui.card()
        if (!title.isNullOrBlank()) card.addView(ui.text(title, 15f, ui.text, true))
        if (!body.isNullOrBlank()) {
            if (!title.isNullOrBlank()) ui.addSpace(card, 5)
            card.addView(ui.text(body, 12f, ui.muted))
        }
        if (accent != null) card.background = ui.rounded(ui.surface, 18, accent)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        return card
    }

    private fun addAction(parent: LinearLayout, label: String, filled: Boolean = false, onClick: () -> Unit) {
        val button = ui.button(label, filled)
        button.setOnClickListener { onClick() }
        parent.addView(button)
    }

    private fun renderHome(parent: LinearLayout) {
        val now = LocalTime.now()
        val date = LocalDate.now()
        val timeCard = addCard(parent)
        val greeting = when (now.hour) { in 5..10 -> "Chào buổi sáng"; in 11..13 -> "Chào buổi trưa"; in 14..17 -> "Chào buổi chiều"; else -> "Chào buổi tối" }
        timeCard.addView(ui.text(greeting, 13f, ui.muted, true))
        clockView = ui.text(now.format(DateTimeFormatter.ofPattern("HH:mm")), 52f, ui.text, true).apply { letterSpacing = -0.03f }
        timeCard.addView(clockView)
        timeCard.addView(ui.text("${TimeUtils.dayName(date.dayOfWeek.value)}, ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", 12f, ui.muted))

        val tasks = db.getTasksForDate(date)
        val minute = now.hour * 60 + now.minute
        val current = tasks.firstOrNull { minute >= it.startMinute && minute < it.endMinute && it.status == "PENDING" }
        val next = tasks.firstOrNull { it.startMinute > minute && it.status == "PENDING" }
        ui.addSpace(parent, 4)
        sectionLabel(parent, "Đang thực hiện")
        val activeCard = addCard(parent, current?.title ?: "Chưa có nhiệm vụ đang diễn ra", current?.let { "${TimeUtils.formatMinute(it.startMinute)}–${TimeUtils.formatMinute(it.endMinute)} · ${it.subject}" } ?: "Đây là khoảng nghỉ hoặc chưa có lịch. Đừng tự bịa ra việc đã hoàn thành.")
        if (current != null) {
            activeCard.addView(ui.text("Còn ${max(0, current.endMinute - minute)} phút", 13f, ui.accent, true))
            activeCard.addView(ui.gap(10))
            val buttons = ui.row()
            addAction(buttons, "Focus 25 phút", true) { startFocusDialog(current.subject, current.id) }
            buttons.addView(ui.hGap(8))
            addAction(buttons, "Hoàn thành") { db.markTask(current.id, date, "COMPLETED"); refreshAll() }
            activeCard.addView(buttons)
        } else {
            addAction(activeCard, "Bắt đầu Focus", true) { startFocusDialog("Tập trung", 0L) }
        }

        sectionLabel(parent, "Tiếp theo")
        val nextCard = addCard(parent, next?.title ?: "Chưa có nhiệm vụ tiếp theo", next?.let {
            "${TimeUtils.formatMinute(it.startMinute)} · ${it.subject} · còn ${max(0, it.startMinute - minute)} phút"
        } ?: "Lịch hôm nay đã kết thúc hoặc chưa có nhiệm vụ được bật.")
        if (next != null) addAction(nextCard, "Xem nhiệm vụ") { showTaskActions(next) }

        val stats = db.getDayStats(date)
        sectionLabel(parent, "Tiến độ hôm nay")
        val progressCard = addCard(parent)
        val row = ui.row()
        row.addView(ui.text("${stats.percent}%", 29f, ui.accent, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ui.text("${stats.completedMinutes}/${stats.plannedMinutes} phút", 12f, ui.muted, true))
        progressCard.addView(row)
        progressCard.addView(ui.hGap(8))
        progressCard.addView(ui.progress(stats.percent))
        progressCard.addView(ui.hGap(8))
        progressCard.addView(ui.text("Tập trung thực tế: ${stats.focusMinutes} phút · Mục tiêu ngày: ${settings.goalMinutes} phút", 12f, ui.muted))
        progressCard.addView(ui.gap(10))
        val actions = ui.row()
        addAction(actions, "Focus Timer", true) { startFocusDialog("Tập trung", current?.id ?: 0L) }
        actions.addView(ui.hGap(8))
        addAction(actions, "Ghi nhật ký") { navigate("JOURNAL") }
        progressCard.addView(actions)

        sectionLabel(parent, "Mục tiêu dài hạn")
        val roadmap = addCard(parent, "Tiến độ lộ trình", "THPT ${db.getRoadmapCompletion("THPT")}%  ·  English ${db.getRoadmapCompletion("ENGLISH")}%  ·  Technology ${db.getRoadmapCompletion("TECH")}%")
        roadmap.addView(ui.progress(db.getRoadmapCompletion()))
        ui.addSpace(parent, 4)
        addAction(parent, "Mở lộ trình học", false) { navigate("ROADMAP") }
    }

    private fun renderSchedule(parent: LinearLayout) {
        heading(parent, "Lịch học", "Mọi ca học đều có thể thêm, sửa và lặp theo ngày.")
        val dateRow = ui.row()
        addAction(dateRow, "‹") { selectedDate = selectedDate.minusDays(1); renderPage() }
        dateRow.addView(ui.text("${TimeUtils.dayName(selectedDate.dayOfWeek.value)} · ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", 13f, ui.text, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, ui.dp(42), 1f))
        addAction(dateRow, "›") { selectedDate = selectedDate.plusDays(1); renderPage() }
        parent.addView(dateRow)
        ui.addSpace(parent, 12)
        val addRow = ui.row()
        addAction(addRow, "+ Thêm nhiệm vụ", true) { showTaskEditor(null, selectedDate) }
        addRow.addView(ui.hGap(8))
        addAction(addRow, "Hôm nay") { selectedDate = LocalDate.now(); renderPage() }
        parent.addView(addRow)
        ui.addSpace(parent, 14)

        val allTasks = db.getTasksForDate(selectedDate, includeDisabled = true)
        if (allTasks.isEmpty()) {
            addCard(parent, "Ngày này chưa có lịch", "Thêm một nhiệm vụ hoặc sao chép lịch từ ngày khác.")
        } else {
            allTasks.forEach { task ->
                val card = addCard(parent)
                val row = ui.row()
                row.addView(ui.text("${TimeUtils.formatMinute(task.startMinute)}\n${TimeUtils.formatMinute(task.endMinute)}", 12f, ui.accent, true), LinearLayout.LayoutParams(ui.dp(58), -2))
                val info = ui.column()
                info.addView(ui.text(task.title, 14f, ui.text, true))
                info.addView(ui.text(task.subject, 11f, ui.muted))
                row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                val status = when (task.status) { "COMPLETED" -> "Xong"; "PARTIAL" -> "Một phần"; "MISSED" -> "Bỏ lỡ"; else -> if (task.enabled) "Chờ" else "Tắt" }
                row.addView(ui.pill(status, when (task.status) { "COMPLETED" -> ui.success; "MISSED" -> ui.danger; "PARTIAL" -> ui.warning; else -> ui.muted }))
                card.addView(row)
                card.setOnClickListener { showTaskActions(task) }
                card.isClickable = true
            }
        }
        ui.addSpace(parent, 4)
        addCard(parent, "Ghi chú lịch", "Nhấn vào một ca để đánh dấu hoàn thành, hoàn thành một phần, bỏ lỡ, sửa hoặc xóa. Nhiệm vụ đã tắt vẫn hiện ở đây để có thể bật lại.")
    }

    private fun showTaskActions(task: ScheduleTask) {
        val date = task.date ?: selectedDate
        val options = listOf("Bắt đầu Focus", "Đánh dấu hoàn thành", "Hoàn thành một phần", "Đánh dấu bỏ lỡ", "Chuyển về chưa làm", if (task.enabled) "Tắt nhiệm vụ" else "Bật nhiệm vụ", "Sửa nhiệm vụ", "Xóa nhiệm vụ")
        AlertDialog.Builder(this).setTitle(task.title).setItems(options.toTypedArray()) { _, which ->
            when (which) {
                0 -> { db.logTaskEvent(task.id, date, "STARTED"); startFocusDialog(task.subject, task.id) }
                1 -> { db.markTask(task.id, date, "COMPLETED"); refreshAll() }
                2 -> { db.markTask(task.id, date, "PARTIAL"); refreshAll() }
                3 -> { db.markTask(task.id, date, "MISSED"); refreshAll() }
                4 -> { db.markTask(task.id, date, "PENDING"); refreshAll() }
                5 -> toggleTaskEnabled(task)
                6 -> showTaskEditor(task, date)
                7 -> AlertDialog.Builder(this).setMessage("Xóa nhiệm vụ lặp này và lịch sử gắn với nó?").setNegativeButton("Hủy", null).setPositiveButton("Xóa") { _, _ -> ReminderScheduler.cancelTask(this, task.id); db.deleteTemplate(task.id); ReminderScheduler.rescheduleAll(this); refreshAll() }.show()
            }
        }.setNegativeButton("Đóng", null).show()
    }

    private fun toggleTaskEnabled(task: ScheduleTask) {
        if (task.enabled) {
            db.setTemplateEnabled(task.id, false)
            if (task.title == "Tiếng Anh buổi sáng") settings.morningStudyEnabled = false
            ReminderScheduler.rescheduleAll(this)
            refreshAll()
            return
        }
        if (settings.hasSleepConflict(task.startMinute, task.endMinute)) {
            toast("Nhiệm vụ nằm trong giờ ngủ. Chỉnh giờ ngủ trước khi bật.")
            return
        }
        val dates = (0L..6L).map { selectedDate.plusDays(it) }
            .filter { (task.daysMask and (1 shl (it.dayOfWeek.value - 1))) != 0 }
        val conflict = dates.firstNotNullOfOrNull { date ->
            db.getTasksForDate(date).firstOrNull { it.id != task.id && task.startMinute < it.endMinute && task.endMinute > it.startMinute }
        }
        if (conflict != null) {
            toast("Không thể bật vì trùng lịch với '${conflict.title}'.")
            return
        }
        db.setTemplateEnabled(task.id, true)
        if (task.title == "Tiếng Anh buổi sáng") settings.morningStudyEnabled = true
        ReminderScheduler.rescheduleAll(this)
        refreshAll()
    }

    private fun showTaskEditor(task: ScheduleTask?, date: LocalDate) {
        val form = ui.column(8)
        fun makeEdit(initial: String, hint: String): EditText = EditText(this).apply {
            setText(initial); this.hint = hint; textSize = 14f; setTextColor(ui.text); setHintTextColor(ui.muted)
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8)); background = ui.rounded(ui.surfaceAlt, 10)
        }
        fun addField(label: String, initial: String, hint: String): EditText {
            form.addView(ui.text(label, 12f, ui.muted, true))
            val edit = makeEdit(initial, hint)
            form.addView(edit, LinearLayout.LayoutParams(-1, ui.dp(46)).apply { bottomMargin = ui.dp(9) })
            return edit
        }
        val title = addField("Tên nhiệm vụ", task?.title ?: "", "Ví dụ: Ôn chương hàm số")
        val subject = addField("Môn / lĩnh vực", task?.subject ?: "", "Toán, English, ESP-IDF...")

        val times = ui.row()
        val startColumn = ui.column()
        startColumn.addView(ui.text("Bắt đầu (HH:mm)", 11f, ui.muted, true))
        val start = makeEdit(TimeUtils.formatMinute(task?.startMinute ?: 19 * 60 + 30), "19:30")
        startColumn.addView(start, LinearLayout.LayoutParams(-1, ui.dp(46)))
        val endColumn = ui.column()
        endColumn.addView(ui.text("Kết thúc (HH:mm)", 11f, ui.muted, true))
        val end = makeEdit(TimeUtils.formatMinute(task?.endMinute ?: 20 * 60 + 30), "20:30")
        endColumn.addView(end, LinearLayout.LayoutParams(-1, ui.dp(46)))
        times.addView(startColumn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
        times.addView(endColumn, LinearLayout.LayoutParams(0, -2, 1f))
        form.addView(times)

        form.addView(ui.text("Lặp vào ngày", 12f, ui.muted, true))
        val dayChecks = mutableListOf<CheckBox>()
        val daysRow = ui.row()
        listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEachIndexed { index, name ->
            val check = CheckBox(this).apply {
                text = name; textSize = 10f; setTextColor(ui.text)
                buttonTintList = android.content.res.ColorStateList.valueOf(ui.accent)
                isChecked = if (task != null) (task.daysMask and (1 shl index)) != 0 else date.dayOfWeek.value == index + 1
                setPadding(0, 0, 0, 0)
            }
            dayChecks.add(check)
            daysRow.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
        }
        form.addView(daysRow)
        form.addView(ui.text("Nhắc trước", 12f, ui.muted, true))
        val reminders = listOf("Đúng giờ", "Trước 5 phút", "Trước 10 phút", "Trước 15 phút", "Không nhắc")
        val reminderValues = listOf(0, 5, 10, 15, -1)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, reminders)
            setSelection(reminderValues.indexOf(task?.reminderMinutes ?: 0).coerceAtLeast(0))
        }
        form.addView(spinner)
        val enabled = CheckBox(this).apply { text = "Kích hoạt nhiệm vụ"; setTextColor(ui.text); isChecked = task?.enabled ?: true }
        val counts = CheckBox(this).apply { text = "Tính vào tiến độ học tập"; setTextColor(ui.text); isChecked = task?.countsForGoal ?: true }
        form.addView(enabled)
        form.addView(counts)

        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this).setTitle(if (task == null) "Thêm nhiệm vụ" else "Sửa nhiệm vụ")
            .setView(scroll).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val startMinute = TimeUtils.parseMinute(start.text.toString())
                val endMinute = TimeUtils.parseMinute(end.text.toString())
                val mask = dayChecks.mapIndexedNotNull { index, check -> if (check.isChecked) 1 shl index else null }.sum()
                when {
                    title.text.isBlank() -> toast("Tên nhiệm vụ không được để trống.")
                    startMinute == null || endMinute == null || endMinute <= startMinute -> toast("Giờ không hợp lệ. Ca học phải kết thúc sau giờ bắt đầu.")
                    mask == 0 -> toast("Chọn ít nhất một ngày lặp.")
                    else -> {
                        val selectedDates = (0L..6L).map { date.plusDays(it) }
                            .filter { (mask and (1 shl (it.dayOfWeek.value - 1))) != 0 }
                        val conflict = if (enabled.isChecked) selectedDates.firstNotNullOfOrNull { checkDate ->
                            db.getTasksForDate(checkDate).firstOrNull { existing ->
                                existing.id != (task?.id ?: 0L) && startMinute < existing.endMinute && endMinute > existing.startMinute
                            }?.let { checkDate to it }
                        } else null
                        if (conflict != null) {
                            toast("Trùng lịch với '${conflict.second.title}' ngày ${conflict.first.format(DateTimeFormatter.ofPattern("dd/MM"))}.")
                            return@setOnClickListener
                        }
                        val savedId = db.saveTemplate(ScheduleTask(
                            id = task?.id ?: 0L, title = title.text.toString(), subject = subject.text.toString(),
                            startMinute = startMinute, endMinute = endMinute, daysMask = mask, enabled = enabled.isChecked,
                            reminderMinutes = reminderValues[spinner.selectedItemPosition], countsForGoal = counts.isChecked
                        ))
                        val sleepConflict = settings.hasSleepConflict(startMinute, endMinute) && enabled.isChecked
                        if (sleepConflict) {
                            db.setTemplateEnabled(savedId, false)
                            AlertDialog.Builder(this).setMessage("Khung giờ này nằm trong giờ ngủ đã cấu hình. Nhiệm vụ đã được lưu nhưng tạm tắt để không nhắc sai giờ.")
                                .setPositiveButton("Đã hiểu", null).show()
                        }
                        if (title.text.toString().trim() == "Tiếng Anh buổi sáng") settings.morningStudyEnabled = enabled.isChecked && !sleepConflict
                        ReminderScheduler.rescheduleAll(this@MainActivity)
                        dialog.dismiss()
                        renderPage()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderJournal(parent: LinearLayout) {
        heading(parent, "Nhật ký học tập", "Chỉ ghi nhận những gì đã xảy ra thật, không cộng điểm cho việc chỉ lên lịch.")
        val dateRow = ui.row()
        addAction(dateRow, "‹") { selectedDate = selectedDate.minusDays(1); renderPage() }
        dateRow.addView(ui.text("${TimeUtils.dayName(selectedDate.dayOfWeek.value)} · ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", 12f, ui.text, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, ui.dp(42), 1f))
        addAction(dateRow, "›") { selectedDate = selectedDate.plusDays(1); renderPage() }
        parent.addView(dateRow)
        ui.addSpace(parent, 12)
        val editorCard = addCard(parent, "Ghi chú hôm nay")
        val note = EditText(this).apply {
            setText(db.getDailyNote(selectedDate)); hint = "Hôm nay hiểu được gì? Còn vướng phần nào?"
            minLines = 4; gravity = Gravity.TOP; textSize = 14f; setTextColor(ui.text); setHintTextColor(ui.muted)
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10)); background = ui.rounded(ui.surfaceAlt, 12)
        }
        editorCard.addView(note, LinearLayout.LayoutParams(-1, -2))
        ui.addSpace(editorCard, 10)
        addAction(editorCard, "Lưu nhật ký", true) { db.saveDailyNote(selectedDate, note.text.toString()); toast("Đã lưu nhật ký."); renderPage() }

        val stats = db.getDayStats(selectedDate)
        val statCard = addCard(parent, "Tổng kết ngày", "${stats.completedMinutes} phút học được ghi nhận trên ${stats.plannedMinutes} phút đã lên lịch · Focus Timer ${stats.focusMinutes} phút · ${stats.percent}%")
        statCard.addView(ui.progress(stats.percent))
        sectionLabel(parent, "Dòng thời gian hoạt động")
        val lines = db.getJournalLines(selectedDate)
        if (lines.isEmpty()) addCard(parent, "Chưa có sự kiện", "Bắt đầu một nhiệm vụ, dùng Focus Timer hoặc lưu ghi chú để tạo nhật ký.")
        else lines.forEach { line ->
            val card = addCard(parent)
            card.addView(ui.text(line, 12f, ui.text))
        }
        sectionLabel(parent, "Thói quen hôm nay")
        val habits = db.getHabitsForDate(selectedDate)
        val habitCard = addCard(parent, "Habit Tracker", "Đánh dấu sau khi thực hiện. Việc lên lịch không tự tính là hoàn thành.")
        if (habits.isEmpty()) habitCard.addView(ui.text("Chưa có thói quen cho ngày này.", 12f, ui.muted))
        else habits.forEach { (id, title, done) ->
            val check = CheckBox(this).apply {
                text = title; isChecked = done; setTextColor(ui.text); buttonTintList = android.content.res.ColorStateList.valueOf(ui.accent)
            }
            check.setOnCheckedChangeListener { _, checked -> db.setHabitDone(id, selectedDate, checked) }
            habitCard.addView(check)
        }
    }

    private fun renderRoadmap(parent: LinearLayout) {
        heading(parent, "Lộ trình", "Mục tiêu có trọng số. Chỉ đánh dấu thành thạo khi có bằng chứng thực hành hoặc kiểm tra.")
        val summary = addCard(parent, "Tiến độ tổng thể", "${db.getRoadmapCompletion()}% hoàn thành theo trọng số milestone")
        summary.addView(ui.progress(db.getRoadmapCompletion()))
        ui.addSpace(parent, 4)
        val addRow = ui.row()
        addAction(addRow, "+ Thêm milestone", true) { showAddMilestoneDialog() }
        parent.addView(addRow)
        ui.addSpace(parent, 12)
        val categories = listOf("THPT" to "THPT", "ENGLISH" to "English", "TECH" to "Technology", "PROJECT" to "Dự án cuối kỳ")
        val all = db.getMilestones()
        categories.forEach { (key, title) ->
            val group = all.filter { it.category == key }
            if (group.isNotEmpty()) {
                sectionLabel(parent, title)
                val percent = db.getRoadmapCompletion(key)
                val cardHead = addCard(parent, "$percent%", "${group.count { it.status == "DONE" }}/${group.size} milestone đã hoàn thành")
                cardHead.addView(ui.progress(percent))
                group.forEach { milestone ->
                    val card = addCard(parent)
                    val row = ui.row()
                    val nameCol = ui.column()
                    nameCol.addView(ui.text(milestone.title, 13f, ui.text, true))
                    if (milestone.dueDate.isNotBlank()) nameCol.addView(ui.text("Hạn: ${milestone.dueDate}", 10f, ui.muted))
                    row.addView(nameCol, LinearLayout.LayoutParams(0, -2, 1f))
                    val statusLabel = when (milestone.status) { "IN_PROGRESS" -> "Đang học"; "PRACTICED" -> "Đã luyện"; "DONE" -> "Thành thạo"; else -> "Chưa học" }
                    row.addView(ui.pill(statusLabel, if (milestone.status == "DONE") ui.success else ui.accent))
                    card.addView(row)
                    if (milestone.description.isNotBlank()) card.addView(ui.text(milestone.description, 11f, ui.muted))
                    if (milestone.notes.isNotBlank()) card.addView(ui.text("Ghi chú: ${milestone.notes}", 11f, ui.muted))
                    card.setOnClickListener { showMilestoneEditor(milestone) }
                }
            }
        }
        ui.addSpace(parent, 4)
        addCard(parent, "Cách tính", "Mỗi milestone có trọng số. Chỉ trạng thái Thành thạo mới tính là hoàn thành 100%; Đã luyện tập thể hiện đang tiến bộ nhưng chưa coi là thành thạo.")
    }

    private fun showMilestoneEditor(milestone: RoadmapMilestone) {
        val statuses = listOf("Chưa học", "Đang học", "Đã luyện tập", "Thành thạo")
        val values = listOf("NOT_STARTED", "IN_PROGRESS", "PRACTICED", "DONE")
        val form = ui.column(8)
        form.addView(ui.text(milestone.title, 15f, ui.text, true))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, statuses)
            setSelection(values.indexOf(milestone.status).coerceAtLeast(0))
        }
        form.addView(ui.text("Trạng thái", 12f, ui.muted)); form.addView(spinner)
        form.addView(ui.text("Mức độ tự tin: ${milestone.confidence}%", 12f, ui.muted))
        val confidence = android.widget.SeekBar(this).apply { max = 100; progress = milestone.confidence }
        form.addView(confidence)
        form.addView(ui.text("Bằng chứng / ghi chú", 12f, ui.muted))
        val notes = EditText(this).apply { setText(milestone.notes); minLines = 3; gravity = Gravity.TOP; setTextColor(ui.text); setHintTextColor(ui.muted); background = ui.rounded(ui.surfaceAlt, 10); setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8)) }
        form.addView(notes)
        AlertDialog.Builder(this).setTitle("Cập nhật tiến độ").setView(form)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ ->
                db.updateMilestone(milestone.id, values[spinner.selectedItemPosition], confidence.progress, notes.text.toString())
                renderPage()
            }.show()
    }

    private fun showAddMilestoneDialog() {
        val form = ui.column(8)
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Technology", "Dự án cuối kỳ", "THPT", "English"))
        }
        form.addView(ui.text("Nhóm", 12f, ui.muted)); form.addView(category)
        val title = EditText(this).apply { hint = "Tên milestone"; setTextColor(ui.text); setHintTextColor(ui.muted); background = ui.rounded(ui.surfaceAlt, 10) }
        form.addView(title)
        val date = EditText(this).apply { hint = "Deadline (YYYY-MM-DD, tùy chọn)"; setTextColor(ui.text); setHintTextColor(ui.muted); background = ui.rounded(ui.surfaceAlt, 10) }
        form.addView(date)
        AlertDialog.Builder(this).setTitle("Thêm milestone").setView(form).setNegativeButton("Hủy", null).setPositiveButton("Thêm") { _, _ ->
            val chosen = category.selectedItemPosition
            val cats = listOf("TECH", "PROJECT", "THPT", "ENGLISH")
            val phase = when (cats[chosen]) { "TECH" -> "Mục tiêu cá nhân"; "PROJECT" -> "Secure AIoT Monitoring System"; "THPT" -> "Mục tiêu THPT"; else -> "English" }
            if (title.text.isNotBlank()) db.addMilestone(cats[chosen], phase, title.text.toString(), date.text.toString())
            renderPage()
        }.show()
    }

    private fun renderAnalytics(parent: LinearLayout) {
        heading(parent, "Thống kê", "Dữ liệu tổng hợp từ xác nhận hoàn thành và Focus Timer.")
        val today = LocalDate.now()
        val stats = db.getDayStats(today)
        val todayCard = addCard(parent, "Hôm nay · ${stats.percent}%", "${stats.completedMinutes} / ${stats.plannedMinutes} phút nhiệm vụ học tập · Focus ${stats.focusMinutes} phút")
        todayCard.addView(ui.progress(stats.percent))
        ui.addSpace(parent, 4)
        sectionLabel(parent, "7 ngày gần nhất")
        val weekStart = today.minusDays(6)
        var plannedTotal = 0
        var completedTotal = 0
        var focusTotal = 0
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            val day = db.getDayStats(date)
            plannedTotal += day.plannedMinutes
            completedTotal += day.completedMinutes
            focusTotal += day.focusMinutes
            val row = ui.row()
            val name = ui.text("${TimeUtils.dayName(date.dayOfWeek.value).take(2)} ${date.format(DateTimeFormatter.ofPattern("dd/MM"))}", 11f, ui.muted)
            row.addView(name, LinearLayout.LayoutParams(ui.dp(86), -2))
            row.addView(ui.progress(day.percent), LinearLayout.LayoutParams(0, ui.dp(10), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            row.addView(ui.text("${day.percent}%", 10f, ui.text, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(ui.dp(38), -2))
            parent.addView(row)
            ui.addSpace(parent, 8)
        }
        val weeklyPercent = if (plannedTotal == 0) 0 else completedTotal * 100 / plannedTotal
        addCard(parent, "Tổng kết 7 ngày", "$weeklyPercent% hoàn thành theo thời lượng · $completedTotal/$plannedTotal phút · Focus Timer $focusTotal phút")
        sectionLabel(parent, "Thời gian theo lĩnh vực")
        val bySubject = db.getWeeklyFocusBySubject(today)
        if (bySubject.isEmpty()) addCard(parent, "Chưa có dữ liệu Focus", "Chạy Focus Timer để ghi nhận thời gian thực tế.")
        else bySubject.forEach { (subject, minutes) -> addCard(parent, subject, "$minutes phút trong tuần") }
        sectionLabel(parent, "Tiến độ lộ trình")
        listOf("THPT" to "Học thuật", "ENGLISH" to "English", "TECH" to "Technology", "PROJECT" to "Dự án") .forEach { (category, label) ->
            val percent = db.getRoadmapCompletion(category)
            val card = addCard(parent, "$label · $percent%")
            card.addView(ui.progress(percent))
        }
        val scores = db.getScoreSummary()
        if (scores.isNotEmpty()) {
            sectionLabel(parent, "Điểm kiểm tra trung bình")
            scores.forEach { (subject, avg) -> addCard(parent, subject, String.format(Locale.getDefault(), "%.2f / 10", avg)) }
        }
        addAction(parent, "+ Nhập điểm kiểm tra", false) { showScoreDialog() }
    }

    private fun showScoreDialog() {
        val form = ui.column(8)
        val subject = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Toán", "Vật lý", "Hóa học", "Tiếng Anh", "Khác")) }
        val score = EditText(this).apply { hint = "Điểm (0–10)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setTextColor(ui.text); setHintTextColor(ui.muted) }
        val note = EditText(this).apply { hint = "Ghi chú (tùy chọn)"; setTextColor(ui.text); setHintTextColor(ui.muted) }
        form.addView(subject); form.addView(score); form.addView(note)
        AlertDialog.Builder(this).setTitle("Thêm điểm kiểm tra").setView(form).setNegativeButton("Hủy", null).setPositiveButton("Lưu") { _, _ ->
            val value = score.text.toString().toDoubleOrNull()
            if (value != null && value in 0.0..10.0) db.addExamScore(subject.selectedItem.toString(), value, LocalDate.now(), note.text.toString())
            else toast("Điểm phải nằm trong khoảng 0–10.")
            renderPage()
        }.show()
    }

    private fun renderSettings(parent: LinearLayout) {
        heading(parent, "Cài đặt", "Tùy chỉnh lịch, thông báo và dữ liệu cục bộ.")
        val themeCard = addCard(parent, "Giao diện", if (settings.darkMode) "Dark mode đang bật" else "Light mode đang bật")
        val themeRow = ui.row()
        addAction(themeRow, "Dark", settings.darkMode) { settings.darkMode = true; applyTheme(); buildBottomNav(); renderPage() }
        themeRow.addView(ui.hGap(8))
        addAction(themeRow, "Light", !settings.darkMode) { settings.darkMode = false; applyTheme(); buildBottomNav(); renderPage() }
        themeCard.addView(themeRow)
        val colorCard = addCard(parent, "Màu nhấn", "Thay đổi màu dùng cho tiến độ và nút chính.")
        val palette = ui.row()
        listOf("#858BFF", "#47B8A5", "#E6A45C", "#D17ACB", "#5C9EE8").forEach { hex ->
            val dot = View(this).apply {
                background = ui.rounded(Color.parseColor(hex), 50)
                layoutParams = LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)).apply { marginEnd = ui.dp(12) }
                setOnClickListener { settings.accent = hex; applyTheme(); renderPage() }
            }
            palette.addView(dot)
        }
        colorCard.addView(palette)

        val sleep = ui.card()
        sleep.addView(ui.text("Giờ ngủ và thức dậy", 15f, ui.text, true))
        sleep.addView(ui.text("Ngủ ${TimeUtils.formatMinute(settings.sleepStart)} · Dậy ${TimeUtils.formatMinute(settings.wakeTime)} · ${settings.sleepDurationMinutes() / 60} giờ ${settings.sleepDurationMinutes() % 60} phút", 12f, ui.muted))
        sleep.addView(ui.text("Khuyến nghị cho tuổi teen thường là 8–10 giờ ngủ mỗi ngày. Lịch hiện tại 7 giờ thấp hơn khoảng khuyến nghị.", 11f, ui.warning))
        ui.addSpace(sleep, 10)
        addAction(sleep, "Chỉnh giờ ngủ / thức dậy", true) { showSleepEditor() }
        parent.addView(sleep, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })

        val morning = addCard(parent, "Ca tự học buổi sáng", "04:30–06:00 · ${if (settings.morningStudyEnabled) "Đang bật" else "Đang tắt"}")
        addAction(morning, if (settings.morningStudyEnabled) "Tắt ca sáng" else "Kiểm tra và bật ca sáng") {
            if (settings.morningStudyEnabled) {
                settings.morningStudyEnabled = false
                db.getAllTemplates().firstOrNull { it.title == "Tiếng Anh buổi sáng" }?.let { db.setTemplateEnabled(it.id, false) }
                ReminderScheduler.rescheduleAll(this); renderPage()
            } else if (settings.hasSleepConflict(270, 360)) {
                AlertDialog.Builder(this).setTitle("Ca học đang trùng giờ ngủ")
                    .setMessage("Giờ ngủ hiện tại bao phủ một phần khung 04:30–06:00. Hãy chỉnh giờ thức dậy sớm hơn 04:30 hoặc đổi khung học trước khi bật. App không tự ý sửa lịch ngủ của bạn.")
                    .setPositiveButton("Chỉnh giờ ngủ") { _, _ -> showSleepEditor() }.setNegativeButton("Để sau", null).show()
            } else {
                settings.morningStudyEnabled = true
                db.getAllTemplates().firstOrNull { it.title == "Tiếng Anh buổi sáng" }?.let { db.setTemplateEnabled(it.id, true) }
                ReminderScheduler.rescheduleAll(this); renderPage()
            }
        }

        val notificationCard = addCard(parent, "Thông báo", notificationStatus())
        addAction(notificationCard, "Cấp quyền thông báo", true) { requestNotificationPermissionIfNeeded(force = true) }
        addAction(notificationCard, "Quyền nhắc đúng giờ", false) { requestExactAlarmAccess() }
        addAction(notificationCard, "Cập nhật lịch nhắc", false) { ReminderScheduler.rescheduleAll(this); toast("Đã đồng bộ lịch nhắc.") }

        val quietCard = addCard(parent, "Chế độ yên lặng", "Không nhắc trong giờ ngủ. Có thể tắt thông báo trong giờ học ở trường.")
        val quietSwitch = Switch(this).apply { text = "Tắt thông báo trong giờ học ở trường"; setTextColor(ui.text); isChecked = settings.schoolQuiet }
        quietSwitch.setOnCheckedChangeListener { _, checked -> settings.schoolQuiet = checked; ReminderScheduler.rescheduleAll(this) }
        quietCard.addView(quietSwitch)
        val reminderSwitch = Switch(this).apply { text = "Bật hệ thống nhắc nhở"; setTextColor(ui.text); isChecked = settings.reminderEnabled }
        reminderSwitch.setOnCheckedChangeListener { _, checked -> settings.reminderEnabled = checked; ReminderScheduler.rescheduleAll(this) }
        quietCard.addView(reminderSwitch)

        val goalCard = addCard(parent, "Mục tiêu tập trung mỗi ngày", "Hiện tại: ${settings.goalMinutes} phút")
        addAction(goalCard, "Thay đổi mục tiêu", false) {
            val input = EditText(this).apply { setText(settings.goalMinutes.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(ui.text) }
            AlertDialog.Builder(this).setTitle("Mục tiêu (phút)").setView(input).setNegativeButton("Hủy", null).setPositiveButton("Lưu") { _, _ ->
                input.text.toString().toIntOrNull()?.let { settings.goalMinutes = it }
                renderPage()
            }.show()
        }

        val backup = addCard(parent, "Backup & Restore", "Xuất toàn bộ database ra JSON hoặc xuất riêng nhật ký dạng CSV. Không cần tài khoản hay kết nối mạng.")
        addAction(backup, "Xuất backup JSON", true) { launchExportJson() }
        ui.addSpace(backup, 8)
        addAction(backup, "Nhập backup JSON", false) { launchImportJson() }
        ui.addSpace(backup, 8)
        addAction(backup, "Xuất nhật ký CSV", false) { launchExportCsv() }
        addCard(parent, "Thông tin ứng dụng", "Focus Clock 1.0.0 · Offline-first · Dữ liệu lưu trong SQLite trên thiết bị. Widget màn hình chính chưa được triển khai trong bản này.")
    }

    private fun showSleepEditor() {
        val form = ui.column(8)
        val sleep = EditText(this).apply { setText(TimeUtils.formatMinute(settings.sleepStart)); hint = "Giờ ngủ HH:mm"; setTextColor(ui.text); setHintTextColor(ui.muted); background = ui.rounded(ui.surfaceAlt, 10) }
        val wake = EditText(this).apply { setText(TimeUtils.formatMinute(settings.wakeTime)); hint = "Giờ thức dậy HH:mm"; setTextColor(ui.text); setHintTextColor(ui.muted); background = ui.rounded(ui.surfaceAlt, 10) }
        form.addView(ui.text("Giờ bắt đầu ngủ", 12f, ui.muted)); form.addView(sleep)
        form.addView(ui.text("Giờ thức dậy", 12f, ui.muted)); form.addView(wake)
        AlertDialog.Builder(this).setTitle("Thiết lập giờ ngủ").setView(form).setNegativeButton("Hủy", null).setPositiveButton("Lưu") { _, _ ->
            val s = TimeUtils.parseMinute(sleep.text.toString())
            val w = TimeUtils.parseMinute(wake.text.toString())
            if (s == null || w == null || s == w) toast("Giờ không hợp lệ.")
            else {
                settings.sleepStart = s; settings.wakeTime = w
                if (settings.hasSleepConflict(270, 360) && settings.morningStudyEnabled) {
                    settings.morningStudyEnabled = false
                    db.getAllTemplates().firstOrNull { it.title == "Tiếng Anh buổi sáng" }?.let { db.setTemplateEnabled(it.id, false) }
                    toast("Ca sáng đã tự tắt vì trùng giờ ngủ mới.")
                }
                ReminderScheduler.rescheduleAll(this)
                renderPage()
            }
        }.show()
    }

    private fun startFocusDialog(subject: String, taskId: Long) {
        val form = ui.column(10)
        form.addView(ui.text("Chọn thời lượng tập trung", 13f, ui.muted))
        val options = listOf("25 phút học / 5 phút nghỉ", "50 phút học / 10 phút nghỉ", "Tùy chỉnh")
        AlertDialog.Builder(this).setTitle("Focus Timer").setItems(options.toTypedArray()) { _, which ->
            when (which) {
                0 -> showRunningTimer(subject, taskId, 25, 5)
                1 -> showRunningTimer(subject, taskId, 50, 10)
                2 -> {
                    val input = EditText(this).apply { hint = "Số phút (1–180)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(ui.text) }
                    AlertDialog.Builder(this).setTitle("Thời lượng tùy chỉnh").setView(input).setNegativeButton("Hủy", null).setPositiveButton("Bắt đầu") { _, _ ->
                        val minutes = input.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 25
                        showRunningTimer(subject, taskId, minutes, 0)
                    }.show()
                }
            }
        }.setNegativeButton("Hủy", null).show()
    }

    private fun showRunningTimer(subject: String, taskId: Long, minutes: Int, breakMinutes: Int) {
        timerSubject = subject.ifBlank { "Tập trung" }
        timerTaskId = taskId
        timerTotalMs = minutes * 60_000L
        timerRemainingMs = timerTotalMs
        timerBreakMinutes = breakMinutes
        timerStartedAt = System.currentTimeMillis()
        timerRunning = true
        val content = ui.column(18)
        val title = ui.text(timerSubject, 14f, ui.muted, true).apply { gravity = Gravity.CENTER }
        val time = ui.text("${minutes.toString().padStart(2, '0')}:00", 46f, ui.text, true).apply { gravity = Gravity.CENTER }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        content.addView(title); content.addView(time); content.addView(progress)
        val buttons = ui.row()
        val pause = ui.button("Tạm dừng")
        val finish = ui.button("Kết thúc", true)
        buttons.addView(pause, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        buttons.addView(ui.hGap(8))
        buttons.addView(finish, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        content.addView(buttons)
        timerDialog = AlertDialog.Builder(this).setTitle("Đang tập trung").setView(content).setNegativeButton("Đóng", null).create()
        timerDialog?.setOnDismissListener { stopTimerAndSave(true) }
        timerDialog?.show()
        pause.setOnClickListener {
            if (timerRunning) {
                focusTimer?.cancel()
                timerRunning = false
                pause.text = "Tiếp tục"
            } else {
                timerRunning = true
                pause.text = "Tạm dừng"
                runTimer(time, progress)
            }
        }
        finish.setOnClickListener {
            val rest = timerBreakMinutes
            timerBreakMinutes = 0
            stopTimerAndSave(true)
            timerDialog?.dismiss()
            if (rest > 0) showBreakTimer(rest)
        }
        runTimer(time, progress)
    }

    private fun runTimer(timeView: TextView, progressBar: ProgressBar) {
        focusTimer?.cancel()
        focusTimer = object : CountDownTimer(timerRemainingMs, 500) {
            override fun onTick(millisUntilFinished: Long) {
                timerRemainingMs = millisUntilFinished
                val seconds = millisUntilFinished / 1000
                timeView.text = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
                progressBar.progress = ((1f - millisUntilFinished.toFloat() / timerTotalMs) * 100).toInt()
            }
            override fun onFinish() {
                timerRemainingMs = 0
                timeView.text = "00:00"
                progressBar.progress = 100
                val rest = timerBreakMinutes
                timerBreakMinutes = 0
                stopTimerAndSave(true)
                toast("Hoàn thành phiên tập trung.")
                timerDialog?.dismiss()
                if (rest > 0) showBreakTimer(rest)
            }
        }.start()
    }

    private fun showBreakTimer(minutes: Int) {
        val content = ui.column(18)
        content.addView(ui.text("Nghỉ ngắn · ${minutes} phút", 14f, ui.muted, true).apply { gravity = Gravity.CENTER })
        val clock = ui.text("${minutes.toString().padStart(2, '0')}:00", 42f, ui.text, true).apply { gravity = Gravity.CENTER }
        content.addView(clock)
        val dialog = AlertDialog.Builder(this).setTitle("Thời gian nghỉ").setView(content).setNegativeButton("Bỏ qua nghỉ", null).create()
        val restTimer = object : CountDownTimer(minutes * 60_000L, 500) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                clock.text = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
            }
            override fun onFinish() {
                clock.text = "00:00"
                toast("Hết giờ nghỉ. Quay lại lịch học nhé.")
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { restTimer.cancel() }
        dialog.show()
        restTimer.start()
    }

    private fun stopTimerAndSave(save: Boolean) {
        if (timerTotalMs <= 0) return
        focusTimer?.cancel()
        val elapsed = ((timerTotalMs - timerRemainingMs).coerceAtLeast(0L) / 60_000L).toInt()
        if (save && elapsed > 0) db.addFocusSession(timerSubject, timerTaskId, LocalDate.now(), timerStartedAt, elapsed, timerRemainingMs == 0L)
        timerTotalMs = 0
        timerRemainingMs = 0
        timerRunning = false
        refreshAll()
    }

    private fun notificationStatus(): String {
        val notifications = if (Build.VERSION.SDK_INT >= 33) checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
        val exact = Build.VERSION.SDK_INT < 31 || (getSystemService(ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
        return "Thông báo: ${if (notifications) "đã cấp quyền" else "chưa cấp quyền"}. Nhắc đúng giờ: ${if (exact) "được phép" else "chưa được phép, dùng fallback không chính xác tuyệt đối"}."
    }

    private fun requestNotificationPermissionIfNeeded(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (force || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            else requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val alarm = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarm.canScheduleExactAlarms()) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
                    .onFailure { toast("Mở Cài đặt ứng dụng và cấp quyền báo thức/nhắc đúng giờ.") }
            } else toast("Quyền nhắc đúng giờ đã được cấp.")
        } else toast("Android phiên bản này không yêu cầu quyền exact alarm riêng.")
    }

    private fun showInitialConflict() {
        settings.conflictShown = true
        AlertDialog.Builder(this).setTitle("Cần xử lý xung đột giờ ngủ")
            .setMessage("Lịch đang đặt giờ ngủ 22:00–05:00 nhưng ca tự học buổi sáng là 04:30–06:00, trùng 30 phút. Ca sáng được để tắt mặc định và app không nhắc học trong giờ ngủ. Bạn có thể chỉnh giờ ngủ trong Cài đặt; app không tự sửa lịch thay bạn. Hiện thời lượng ngủ là 7 giờ, thấp hơn khuyến nghị 8–10 giờ cho tuổi teen.")
            .setPositiveButton("Đã hiểu", null).setNeutralButton("Chỉnh giờ ngủ") { _, _ -> navigate("SETTINGS"); showSleepEditor() }.show()
    }

    private fun launchExportJson() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "focus-clock-backup-${LocalDate.now()}.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_JSON)
    }

    private fun launchImportJson() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }
        startActivityForResult(intent, REQUEST_IMPORT_JSON)
    }

    private fun launchExportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "focus-clock-journal-${LocalDate.now()}.csv")
        }
        startActivityForResult(intent, REQUEST_EXPORT_CSV)
    }

    @Deprecated("Storage Access Framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_EXPORT_JSON -> contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(db.exportJson(settings)) }
                REQUEST_EXPORT_CSV -> contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(db.exportJournalCsv()) }
                REQUEST_IMPORT_JSON -> {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Không đọc được tệp.")
                    AlertDialog.Builder(this).setTitle("Nhập backup")
                        .setMessage("Nhập backup sẽ thay thế dữ liệu hiện tại trong ứng dụng. Hãy xuất backup hiện tại trước nếu cần giữ lại.")
                        .setNegativeButton("Hủy", null).setPositiveButton("Nhập dữ liệu") { _, _ ->
                            runCatching { db.importJson(json, settings); applyTheme(); buildBottomNav(); ReminderScheduler.rescheduleAll(this); toast("Khôi phục dữ liệu thành công."); renderPage() }
                                .onFailure { toast("Không thể nhập backup: ${it.message}") }
                        }.show()
                }
            }
            if (requestCode == REQUEST_EXPORT_JSON || requestCode == REQUEST_EXPORT_CSV) toast("Đã xuất tệp thành công.")
        } catch (e: Exception) { toast("Lỗi tệp: ${e.message}") }
    }

    private fun refreshAll() {
        buildBottomNav(); renderPage(); ReminderScheduler.rescheduleAll(this)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
