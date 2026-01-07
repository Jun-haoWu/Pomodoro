package com.wujunhao.a202302010306.pomodoro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.media.MediaPlayer
import android.util.Log
import android.net.Uri
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.TimeUnit
import android.graphics.Color
import android.widget.EditText
import android.os.Handler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    private var maxTime = 25 * 60
    private var currentTime = 25 * 60
    private var progress = 1f

    init {
        backgroundPaint.color = ContextCompat.getColor(context, android.R.color.darker_gray)
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = 20f

        progressPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_bright)
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = 20f
        progressPaint.strokeCap = Paint.Cap.ROUND

        textPaint.color = ContextCompat.getColor(context, android.R.color.white)
        textPaint.textSize = 80f
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) - 40f

        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)
        canvas.drawArc(rectF, -90f, 360f * progress, false, progressPaint)

        val minutes = currentTime / 60
        val seconds = currentTime % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        canvas.drawText(timeText, centerX, centerY + (textPaint.textSize / 3), textPaint)
    }

    fun setMaxTime(seconds: Int) {
        maxTime = seconds
        currentTime = seconds
        progress = 1f
        invalidate()
    }

    fun setCurrentTime(seconds: Int) {
        currentTime = seconds
        progress = currentTime.toFloat() / maxTime
        invalidate()
    }

    fun animateProgress(fromSeconds: Int, toSeconds: Int, duration: Long) {
        val animator = ValueAnimator.ofFloat(fromSeconds.toFloat(), toSeconds.toFloat())
        animator.duration = duration
        animator.addUpdateListener { animation ->
            val currentSeconds = animation.animatedValue as Float
            setCurrentTime(currentSeconds.toInt())
        }
        animator.start()
    }
}

class WhiteNoisePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentNoiseType: NoiseType? = null

    enum class NoiseType(val resourceId: Int) {
        RAIN(R.raw.rain),
        WAVE(R.raw.wave)
    }

    fun play(noiseType: NoiseType) {
        android.util.Log.d("WhiteNoisePlayer", "Playing noise type: ${noiseType.name}")
        stop()
        currentNoiseType = noiseType
        try {
            mediaPlayer = MediaPlayer.create(context, noiseType.resourceId)
            if (mediaPlayer == null) {
                android.util.Log.e("WhiteNoisePlayer", "Failed to create MediaPlayer for ${noiseType.name}")
                return
            }
            mediaPlayer?.apply {
                isLooping = true
                setVolume(0.5f, 0.5f)
                start()
                android.util.Log.d("WhiteNoisePlayer", "Successfully started playing ${noiseType.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("WhiteNoisePlayer", "Error playing ${noiseType.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun pause() {
        mediaPlayer?.apply {
            if (isPlaying) {
                pause()
            }
        }
    }

    fun resume() {
        mediaPlayer?.apply {
            if (!isPlaying) {
                start()
            }
        }
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        currentNoiseType = null
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun getCurrentNoiseType(): NoiseType? {
        return currentNoiseType
    }

    fun setVolume(leftVolume: Float, rightVolume: Float) {
        mediaPlayer?.setVolume(leftVolume, rightVolume)
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var countdownView: CountdownView
    private lateinit var whiteNoisePlayer: WhiteNoisePlayer
    private lateinit var btnStartPause: android.widget.Button
    private lateinit var btnReset: android.widget.Button
    private lateinit var btnReminders: android.widget.Button
    private var isRunning = false
    private var remainingTime = 30 * 60 // 默认30分钟
    private var timer: android.os.Handler? = null
    private var runnable: Runnable? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var currentTimeInMinutes = 30 // 当前选择的分钟数
    private lateinit var rbRain: android.widget.RadioButton
    private lateinit var rbWave: android.widget.RadioButton
    
    // 提醒功能相关变量
    private val reminders = mutableListOf<Reminder>() // 提醒列表
    private var reminderMediaPlayer: MediaPlayer? = null // 提醒音效播放器
    
    // 倒计时设置对话框
    private var timerDialog: AlertDialog? = null
    private lateinit var tvNextReminder: TextView
    private var reminderHandler: Handler? = null
    private var reminderRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        
        // 监听SharedPreferences变化
        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sync_audio_with_timer" -> {
                    // 当同步音频设置改变时的处理逻辑
                }
            }
        }
        
        // 监听提醒更新
        val reminderPrefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        reminderPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "lastUpdate") {
                loadReminders()
                updateNextReminderDisplay()
            }
        }
        
        countdownView = findViewById(R.id.countdownView)
        whiteNoisePlayer = WhiteNoisePlayer(this)

        btnStartPause = findViewById(R.id.btnStartPause)
        btnReset = findViewById(R.id.btnReset)
        val btnSettings = findViewById<android.widget.ImageButton>(R.id.btnSettings)
        rbRain = findViewById(R.id.rbRain)
        rbWave = findViewById<android.widget.RadioButton>(R.id.rbWave)
        btnReminders = findViewById(R.id.btnReminders)
        tvNextReminder = findViewById(R.id.tvNextReminder)
        
        // 初始化提醒列表
        loadReminders()
        
        // 启动提醒倒计时更新
        startReminderCountdown()
        
        // 设置默认选中的白噪音
        rbRain.isChecked = true

        btnStartPause.setOnClickListener {
            if (!isRunning) {
                // 如果当前没有运行，显示倒计时设置对话框
                showTimerSettingDialog()
            } else {
                // 如果当前正在运行，暂停计时
                pauseTimer()
                if (::btnStartPause.isInitialized) {
                    btnStartPause.text = getString(R.string.resume)
                }
            }
        }

        btnReset.setOnClickListener {
            resetTimer()
            if (::btnStartPause.isInitialized) {
                btnStartPause.text = getString(R.string.start)
            }
        }

        btnSettings.setOnClickListener {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        btnReminders.setOnClickListener {
            val intent = android.content.Intent(this, RemindersActivity::class.java)
            startActivity(intent)
        }

        rbRain.setOnClickListener {
            if (rbRain.isChecked) {
                whiteNoisePlayer.play(WhiteNoisePlayer.NoiseType.RAIN)
            }
        }

        rbWave.setOnClickListener {
            if (rbWave.isChecked) {
                whiteNoisePlayer.play(WhiteNoisePlayer.NoiseType.WAVE)
            }
        }



    }

    private fun startTimer() {
        if (!isRunning) {
            // 重置所有提醒的触发状态
            reminders.forEach { it.isTriggered = false }
            
            isRunning = true
            
            // 播放选中的白噪音（不管同步是否开启，都应该播放音频）
            if (rbRain.isChecked) {
                whiteNoisePlayer.play(WhiteNoisePlayer.NoiseType.RAIN)
            } else if (rbWave.isChecked) {
                whiteNoisePlayer.play(WhiteNoisePlayer.NoiseType.WAVE)
            }
            
            timer = android.os.Handler(android.os.Looper.getMainLooper())
            runnable = object : Runnable {
                override fun run() {
                    if (remainingTime > 0) {
                        remainingTime--
                        countdownView.setCurrentTime(remainingTime)
                        
                        timer?.postDelayed(this, 1000)
                    } else {
                        stopTimer()
                        // 倒计时结束时重置按钮文本
                        if (::btnStartPause.isInitialized) {
                            btnStartPause.text = getString(R.string.start)
                        }
                    }
                }
            }
            runnable?.let { timer?.post(it) }
        }
    }

    private fun pauseTimer() {
        isRunning = false
        runnable?.let { timer?.removeCallbacks(it) }
        
        // 检查是否启用了同步音频功能
        val isSyncEnabled = sharedPreferences.getBoolean("sync_audio_with_timer", false)
        if (isSyncEnabled && whiteNoisePlayer.isPlaying()) {
            // 同步暂停音频（只有当音频正在播放时才暂停）
            whiteNoisePlayer.pause()
        }
    }

    private fun stopTimer() {
        isRunning = false
        runnable?.let { timer?.removeCallbacks(it) }
        
        // 重置按钮文本为"开始"（添加空值保护）
        if (::btnStartPause.isInitialized) {
            btnStartPause.text = getString(R.string.start)
        }
        
        // 检查是否启用了同步音频功能
        val isSyncEnabled = sharedPreferences.getBoolean("sync_audio_with_timer", false)
        if (isSyncEnabled && whiteNoisePlayer.isPlaying()) {
            // 同步停止音频（只有当音频正在播放时才停止）
            whiteNoisePlayer.stop()
        }
    }

    private fun setTimerDuration(minutes: Int) {
        if (!isRunning) {
            currentTimeInMinutes = minutes
            remainingTime = minutes * 60
            countdownView.setMaxTime(remainingTime)
        }
    }

    private fun showCustomTimeDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "输入分钟数 (1-120)"
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_custom_time))
            .setMessage(getString(R.string.enter_minutes))
            .setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes in 1..120) {
                    setTimerDuration(minutes)
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.invalid_time_range), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                // 取消操作
            }
            .setOnCancelListener {
                // 取消操作
            }
            .show()
    }

    private fun resetTimer() {
        stopTimer()
        remainingTime = currentTimeInMinutes * 60
        countdownView.setMaxTime(remainingTime)
        // 重置按钮文本为"开始"（添加空值保护）
        if (::btnStartPause.isInitialized) {
            btnStartPause.text = getString(R.string.start)
        }
        
        // 重置所有提醒的触发状态
        reminders.forEach { it.isTriggered = false }
        
        // 停止提醒音效
        reminderMediaPlayer?.release()
        reminderMediaPlayer = null
    }
    
    // 显示倒计时设置对话框
    private fun showTimerSettingDialog() {
        // 如果对话框已经显示，不重复创建
        if (timerDialog != null && timerDialog?.isShowing == true) {
            return
        }
        
        // 创建对话框视图
        val dialogView = layoutInflater.inflate(R.layout.dialog_timer_setting, null)
        
        // 获取对话框中的控件
        val radioGroupTimer = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupTimer)
        val rb5min = dialogView.findViewById<android.widget.RadioButton>(R.id.rb5min)
        val rb15min = dialogView.findViewById<android.widget.RadioButton>(R.id.rb15min)
        val rb25min = dialogView.findViewById<android.widget.RadioButton>(R.id.rb25min)
        val rb45min = dialogView.findViewById<android.widget.RadioButton>(R.id.rb45min)
        val rb60min = dialogView.findViewById<android.widget.RadioButton>(R.id.rb60min)
        val rbCustom = dialogView.findViewById<android.widget.RadioButton>(R.id.rbCustom)
        val etCustomTime = dialogView.findViewById<android.widget.EditText>(R.id.etCustomTime)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        
        // 根据当前时间设置默认选中项
        when (currentTimeInMinutes) {
            5 -> rb5min.isChecked = true
            15 -> rb15min.isChecked = true
            25 -> rb25min.isChecked = true
            45 -> rb45min.isChecked = true
            60 -> rb60min.isChecked = true
            else -> {
                rbCustom.isChecked = true
                etCustomTime.setText(currentTimeInMinutes.toString())
                etCustomTime.visibility = android.view.View.VISIBLE
            }
        }
        
        // 设置自定义选项的显示/隐藏逻辑
        radioGroupTimer.setOnCheckedChangeListener { _, checkedId ->
            etCustomTime.visibility = if (checkedId == R.id.rbCustom) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
        
        // 创建对话框
        timerDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 设置取消按钮点击事件
        btnCancel.setOnClickListener {
            timerDialog?.dismiss()
        }
        
        // 设置确定按钮点击事件
        btnConfirm.setOnClickListener {
            var selectedMinutes = 0
            
            when (radioGroupTimer.checkedRadioButtonId) {
                R.id.rb5min -> selectedMinutes = 5
                R.id.rb15min -> selectedMinutes = 15
                R.id.rb25min -> selectedMinutes = 25
                R.id.rb45min -> selectedMinutes = 45
                R.id.rb60min -> selectedMinutes = 60
                R.id.rbCustom -> {
                    val customTime = etCustomTime.text.toString()
                    if (customTime.isNotEmpty()) {
                        val customMinutes = customTime.toIntOrNull()
                        if (customMinutes != null && customMinutes > 0 && customMinutes <= 120) {
                            selectedMinutes = customMinutes
                        } else {
                            Toast.makeText(this, getString(R.string.invalid_time_range), Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.invalid_time_range), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
            }
            
            if (selectedMinutes > 0) {
                currentTimeInMinutes = selectedMinutes
                remainingTime = selectedMinutes * 60
                countdownView.setMaxTime(remainingTime)
                countdownView.setCurrentTime(remainingTime)
                
                // 开始计时
                startTimer()
                if (::btnStartPause.isInitialized) {
                    btnStartPause.text = getString(R.string.pause)
                }
                
                timerDialog?.dismiss()
            }
        }
        
        // 显示对话框
        timerDialog?.show()
    }
    
    private fun triggerReminder(reminder: Reminder) {
        // 播放提醒音效
        playReminderSound()
        
        // 显示提醒通知
        Toast.makeText(this, "提醒: ${reminder.text}", Toast.LENGTH_LONG).show()
    }
    
    private fun playReminderSound() {
        try {
            // 释放之前的提醒音效
            reminderMediaPlayer?.release()
            
            // 使用系统默认通知音作为提醒音
            reminderMediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            reminderMediaPlayer?.setOnCompletionListener {
                it.release()
                reminderMediaPlayer = null
            }
            reminderMediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadReminders() {
        val prefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("reminders", null)
        
        if (json != null) {
            val type = object : TypeToken<MutableList<Reminder>>() {}.type
            reminders.clear()
            reminders.addAll(gson.fromJson(json, type))
        }
        
        // 检查是否有提醒需要触发
        val currentTime = System.currentTimeMillis()
        val triggeredReminders = reminders.filter { it.time <= currentTime && !it.isTriggered }
        if (triggeredReminders.isNotEmpty()) {
            triggeredReminders.forEach { reminder ->
                triggerReminder(reminder)
                reminder.isTriggered = true
            }
            saveReminders()
        }
    }
    
    private fun saveReminders() {
        val prefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = Gson()
        val json = gson.toJson(reminders)
        editor.putString("reminders", json)
        editor.apply()
    }
    
    private fun startReminderCountdown() {
        reminderHandler = Handler()
        reminderRunnable = object : Runnable {
            override fun run() {
                updateNextReminderDisplay()
                reminderHandler?.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        reminderHandler?.post(reminderRunnable!!)
    }
    
    private fun updateNextReminderDisplay() {
        if (reminders.isEmpty()) {
            tvNextReminder.text = "暂无提醒事项"
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // 检查是否有提醒需要触发
        val triggeredReminders = reminders.filter { it.time <= currentTime && !it.isTriggered }
        triggeredReminders.forEach { reminder ->
            triggerReminder(reminder)
            reminder.isTriggered = true
        }
        
        // 保存更新后的提醒状态
        if (triggeredReminders.isNotEmpty()) {
            saveReminders()
        }
        
        // 获取即将到来的提醒
        val upcomingReminders = reminders.filter { it.time > currentTime }
        
        if (upcomingReminders.isEmpty()) {
            tvNextReminder.text = "暂无即将到来的提醒事项"
            return
        }
        
        val nextReminder = upcomingReminders.minByOrNull { it.time }!!
        val timeRemaining = nextReminder.time - currentTime
        
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(timeRemaining)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
        
        val timeText = when {
            hours > 0 -> "${hours}小时${minutes}分钟${seconds}秒后"
            minutes > 0 -> "${minutes}分钟${seconds}秒后"
            else -> "${seconds}秒后"
        }
        
        tvNextReminder.text = "${timeText}: ${nextReminder.text}"
    }

    override fun onResume() {
        super.onResume()
        // 每次回到主界面时重新加载提醒列表
        loadReminders()
        updateNextReminderDisplay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        runnable?.let { timer?.removeCallbacks(it) }
        whiteNoisePlayer.stop()
        
        // 释放提醒音效播放器
        reminderMediaPlayer?.release()
        reminderMediaPlayer = null
    }
    

}

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchSyncAudio: android.widget.Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        
        sharedPreferences = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        switchSyncAudio = findViewById(R.id.switchSyncAudio)
        
        // 加载保存的设置
        val isSyncEnabled = sharedPreferences.getBoolean("sync_audio_with_timer", false)
        switchSyncAudio.isChecked = isSyncEnabled
        
        switchSyncAudio.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("sync_audio_with_timer", isChecked).apply()
        }
    }
}