package com.wujunhao.a202302010306.pomodoro

// 提醒数据类
data class Reminder(
    val id: String,
    val time: Long, // 使用毫秒时间戳
    val text: String,
    var isTriggered: Boolean = false
)