package com.example.huaweiblocker

// Простая модель данных для одного подключенного устройства
data class Device(
    val name: String,
    val mac: String,
    var isBlocked: Boolean,
    val is5GHz: Boolean // Чтобы разделять девайсы по вкладкам частот
)
