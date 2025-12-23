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
        stop()
        currentNoiseType = noiseType
        try {
            mediaPlayer = MediaPlayer.create(context, noiseType.resourceId)
            mediaPlayer?.apply {
                isLooping = true
                setVolume(0.5f, 0.5f)
                start()
            }
        } catch (e: Exception) {
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
    private var remainingTime = 25 * 60
    private var timer: android.os.Handler? = null
    private var runnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        countdownView = findViewById(R.id.countdownView)
        whiteNoisePlayer = WhiteNoisePlayer(this)

        val btnStart = findViewById<android.widget.Button>(R.id.btnStart)
        val btnPause = findViewById<android.widget.Button>(R.id.btnPause)
        val btnReset = findViewById<android.widget.Button>(R.id.btnReset)
        val rbRain = findViewById<android.widget.RadioButton>(R.id.rbRain)
        val rbWave = findViewById<android.widget.RadioButton>(R.id.rbWave)

        btnStart.setOnClickListener {
            startTimer()
        }

        btnPause.setOnClickListener {
            pauseTimer()
        }

        btnReset.setOnClickListener {
            resetTimer()
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
            isRunning = true
            
            // 自动播放选中的白噪音
            val rbRain = findViewById<android.widget.RadioButton>(R.id.rbRain)
            val rbWave = findViewById<android.widget.RadioButton>(R.id.rbWave)
            
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
                    }
                }
            }
            timer?.post(runnable!!)
        }
    }

    private fun pauseTimer() {
        isRunning = false
        timer?.removeCallbacks(runnable!!)
    }

    private fun resetTimer() {
        stopTimer()
        remainingTime = 25 * 60
        countdownView.setMaxTime(remainingTime)
    }

    private fun stopTimer() {
        isRunning = false
        timer?.removeCallbacks(runnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        whiteNoisePlayer.stop()
    }
}