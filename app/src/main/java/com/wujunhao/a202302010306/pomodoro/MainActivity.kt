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
    private var isRunning = false
    private var remainingTime = 30 * 60 // 默认30分钟
    private var timer: android.os.Handler? = null
    private var runnable: Runnable? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var currentTimeInMinutes = 30 // 当前选择的分钟数
    private lateinit var rbRain: android.widget.RadioButton
    private lateinit var rbWave: android.widget.RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        countdownView = findViewById(R.id.countdownView)
        whiteNoisePlayer = WhiteNoisePlayer(this)

        val btnStart = findViewById<android.widget.Button>(R.id.btnStart)
        val btnPause = findViewById<android.widget.Button>(R.id.btnPause)
        val btnReset = findViewById<android.widget.Button>(R.id.btnReset)
        val btnSettings = findViewById<android.widget.ImageButton>(R.id.btnSettings)
        rbRain = findViewById(R.id.rbRain)
        rbWave = findViewById<android.widget.RadioButton>(R.id.rbWave)
        val rb10min = findViewById<android.widget.RadioButton>(R.id.rb10min)
        val rb20min = findViewById<android.widget.RadioButton>(R.id.rb20min)
        val rb30min = findViewById<android.widget.RadioButton>(R.id.rb30min)
        val rbCustom = findViewById<android.widget.RadioButton>(R.id.rbCustom)

        // 设置默认选中的时间和白噪音
        rb30min.isChecked = true
        rbRain.isChecked = true

        btnStart.setOnClickListener {
            startTimer()
        }

        btnPause.setOnClickListener {
            pauseTimer()
        }

        btnReset.setOnClickListener {
            resetTimer()
        }

        btnSettings.setOnClickListener {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
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

        // 时间选择监听器
        rb10min.setOnClickListener {
            if (rb10min.isChecked) {
                setTimerDuration(10)
            }
        }

        rb20min.setOnClickListener {
            if (rb20min.isChecked) {
                setTimerDuration(20)
            }
        }

        rb30min.setOnClickListener {
            if (rb30min.isChecked) {
                setTimerDuration(30)
            }
        }

        rbCustom.setOnClickListener {
            if (rbCustom.isChecked) {
                showCustomTimeDialog()
            }
        }
    }

    private fun startTimer() {
        if (!isRunning) {
            isRunning = true
            
            val isSyncEnabled = sharedPreferences.getBoolean("sync_audio_with_timer", false)
            
            if (isSyncEnabled) {
                // 如果同步开启，先尝试恢复被暂停的音频
                if (whiteNoisePlayer.isPlaying()) {
                    // 如果已经在播放，不需要重新播放
                } else {
                    // 音频被暂停了，恢复播放
                    whiteNoisePlayer.resume()
                }
            } else {
                // 如果同步未开启，按原来的逻辑播放
                if (rbRain.isChecked) {
                    whiteNoisePlayer.play(WhiteNoisePlayer.NoiseType.RAIN)
                } else if (rbWave.isChecked) {
                    whiteNoisePlayer.play(WhiteNoisePlayer.NoiseType.WAVE)
                }
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
                    }
                }
            }
            timer?.post(runnable!!)
        }
    }

    private fun pauseTimer() {
        isRunning = false
        timer?.removeCallbacks(runnable!!)
        
        // 检查是否启用了同步音频功能
        val isSyncEnabled = sharedPreferences.getBoolean("sync_audio_with_timer", false)
        if (isSyncEnabled) {
            // 同步暂停音频
            whiteNoisePlayer.pause()
        }
    }

    private fun stopTimer() {
        isRunning = false
        timer?.removeCallbacks(runnable!!)
        
        // 检查是否启用了同步音频功能
        val isSyncEnabled = sharedPreferences.getBoolean("sync_audio_with_timer", false)
        if (isSyncEnabled) {
            // 同步停止音频
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
                    // 恢复默认选择
                    findViewById<android.widget.RadioButton>(R.id.rb30min).isChecked = true
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                // 恢复默认选择
                findViewById<android.widget.RadioButton>(R.id.rb30min).isChecked = true
            }
            .setOnCancelListener {
                // 恢复默认选择
                findViewById<android.widget.RadioButton>(R.id.rb30min).isChecked = true
            }
            .show()
    }

    private fun resetTimer() {
        stopTimer()
        remainingTime = currentTimeInMinutes * 60
        countdownView.setMaxTime(remainingTime)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        whiteNoisePlayer.stop()
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