package com.wujunhao.a202302010306.pomodoro

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.widget.EditText
import android.widget.Toast
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RemindersActivity : AppCompatActivity() {
    
    private lateinit var reminderListLayout: LinearLayout
    private lateinit var etReminderTime: EditText
    private lateinit var etReminderText: EditText
    private lateinit var btnAddReminder: ImageButton
    
    // 提醒列表
    private val reminders = mutableListOf<Reminder>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reminders_activity)
        
        // 初始化视图
        reminderListLayout = findViewById(R.id.reminderListLayout)
        etReminderTime = findViewById(R.id.etReminderTime)
        etReminderText = findViewById(R.id.etReminderText)
        btnAddReminder = findViewById(R.id.btnAddReminder)
        
        // 设置工具栏
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // 设置添加提醒按钮点击事件
        btnAddReminder.setOnClickListener {
            addReminder()
        }
        
        // 初始化提醒列表显示
        updateReminderListDisplay()
    }
    
    // 添加提醒
    private fun addReminder() {
        val timeText = etReminderTime.text.toString()
        val reminderText = etReminderText.text.toString()
        
        if (timeText.isEmpty() || reminderText.isEmpty()) {
            Toast.makeText(this, "请填写完整的提醒信息", Toast.LENGTH_SHORT).show()
            return
        }
        
        val minutes = timeText.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            Toast.makeText(this, "请输入有效的时间", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 将分钟转换为毫秒时间戳
        val currentTime = System.currentTimeMillis()
        val timeInMilliseconds = currentTime + (minutes * 60 * 1000)
        
        val reminderId = System.currentTimeMillis().toString()
        val reminder = Reminder(reminderId, timeInMilliseconds, reminderText)
        reminders.add(reminder)
        
        // 保存提醒列表
        saveReminders()
        
        // 清空输入框
        etReminderTime.text.clear()
        etReminderText.text.clear()
        
        // 更新UI显示
        updateReminderListDisplay()
        
        // 通知MainActivity更新提醒显示
        notifyMainActivityUpdate()
        
        Toast.makeText(this, "提醒添加成功", Toast.LENGTH_SHORT).show()
    }
    
    // 删除提醒
    private fun deleteReminder(reminderId: String) {
        reminders.removeAll { it.id == reminderId }
        
        // 保存提醒列表
        saveReminders()
        
        updateReminderListDisplay()
        
        // 通知MainActivity更新提醒显示
        notifyMainActivityUpdate()
        
        Toast.makeText(this, "提醒已删除", Toast.LENGTH_SHORT).show()
    }
    
    private fun notifyMainActivityUpdate() {
        // 使用SharedPreferences通知MainActivity更新提醒显示
        val prefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
    }
    
    // 更新提醒列表显示
    private fun updateReminderListDisplay() {
        reminderListLayout.removeAllViews()
        
        if (reminders.isEmpty()) {
            val noRemindersText = TextView(this).apply {
                text = getString(R.string.no_reminders)
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 14f
                setPadding(16, 16, 16, 16)
            }
            reminderListLayout.addView(noRemindersText)
        } else {
            reminders.forEach { reminder ->
                val reminderView = createReminderView(reminder)
                reminderListLayout.addView(reminderView)
            }
        }
    }
    
    // 创建单个提醒项的视图
    private fun createReminderView(reminder: Reminder): View {
        val reminderView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            tag = reminder.id
        }
        
        // 时间文本
        val timeView = TextView(this).apply {
            id = R.id.tvReminderItemTime
            // 计算相对时间
            val currentTime = System.currentTimeMillis()
            val timeRemaining = reminder.time - currentTime
            val minutesRemaining = (timeRemaining / (1000 * 60)).toInt()
            
            text = if (minutesRemaining > 0) {
                "${minutesRemaining}分钟后"
            } else {
                "已过期"
            }
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            setPadding(0, 0, 16, 0)
        }
        
        // 提醒内容文本
        val textView = TextView(this).apply {
            id = R.id.tvReminderItemText
            text = reminder.text
            setTextColor(if (reminder.isTriggered) Color.parseColor("#FF6B6B") else Color.parseColor("#FFFFFF"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        // 删除按钮
        val deleteButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundResource(android.R.drawable.btn_default)
            contentDescription = getString(R.string.delete_reminder)
            setOnClickListener {
                deleteReminder(reminder.id)
            }
        }
        
        reminderView.addView(timeView)
        reminderView.addView(textView)
        reminderView.addView(deleteButton)
        
        return reminderView
    }
    
    // 获取提醒列表（供MainActivity使用）
    fun getReminders(): List<Reminder> {
        return reminders.toList()
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
    }
    
    private fun saveReminders() {
        val prefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = Gson()
        val json = gson.toJson(reminders)
        editor.putString("reminders", json)
        editor.apply()
    }
}