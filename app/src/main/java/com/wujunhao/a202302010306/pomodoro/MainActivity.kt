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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}