package com.example.huaweiblocker

import com.example.huaweiblocker.R
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var btn5GHz: Button
    private lateinit var btn24GHz: Button

    private var allDevices = mutableListOf<Device>()
    private var isCurrent5GHz = true
    private var sessionCookie: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvDevices = findViewById(R.id.rvDevices)
        btn5GHz = findViewById(R.id.btn5GHz)
        btn24GHz = findViewById(R.id.btn24GHz)

        rvDevices.layoutManager = LinearLayoutManager(this)

        deviceAdapter = DeviceAdapter(emptyList(), { selectedDevice ->
            toggleDeviceBlockStatus(selectedDevice)
        })
        rvDevices.adapter = deviceAdapter

        btn5GHz.setOnClickListener {
            switchFrequency(true)
        }

        btn24GHz.setOnClickListener {
            switchFrequency(false)
        }

        loadDataFromRouter()
    }

    private fun switchFrequency(is5GHz: Boolean) {
        isCurrent5GHz = is5GHz
        if (is5GHz) {
            btn5GHz.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#29B6F6")))
            btn5GHz.setTextColor(Color.WHITE)
            btn24GHz.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2C2C2C")))
            btn24GHz.setTextColor(Color.parseColor("#888888"))
        } else {
            btn24GHz.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#29B6F6")))
            btn24GHz.setTextColor(Color.WHITE)
            btn5GHz.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2C2C2C")))
            btn5GHz.setTextColor(Color.parseColor("#888888"))
        }
        filterAndDisplayDevices()
    }

    private fun filterAndDisplayDevices() {
        val filtered = allDevices.filter { it.is5GHz == isCurrent5GHz }
        deviceAdapter.updateList(filtered)
    }

    private fun loadDataFromRouter() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = loginToRouter()
                if (success) {
                    fetchDevicesFromRouter()
                } else {
                    createMockDevices()
                }
                withContext(Dispatchers.Main) {
                    filterAndDisplayDevices()
                    Toast.makeText(this@MainActivity, "Данные обновлены", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                createMockDevices()
                withContext(Dispatchers.Main) {
                    filterAndDisplayDevices()
                    Toast.makeText(this@MainActivity, "Режим демо: роутер недоступен", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loginToRouter(): Boolean {
        val loginUrl = URL("http://192.168.1.1/login.cgi")
        val conn = loginUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000

        val postData = "UserName=telekom&PassWord=dGVsZWtvbQ%3D%3D&Language=english"
        
        conn.outputStream.use { os ->
            val input = postData.toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val cookies = conn.headerFields["Set-Cookie"]
            if (!cookies.isNullOrEmpty()) {
                sessionCookie = cookies[0].split(";")[0]
            }
            return true
        }
        return false
    }

    private fun fetchDevicesFromRouter() {
        val pageUrl = URL("http://192.168.1.1/html/bbsp/common/GetLanUserDevInfo.asp")
        val conn = pageUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        sessionCookie?.let { conn.setRequestProperty("Cookie", it) }

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var html = reader.use { it.readText() }

            // Декодируем hex-символы роутера (\x2d -> -, \x3a -> :)
            html = html.replace("\\x2d", "-").replace("\\x3a", ":")

            allDevices.clear()

            // Парсим конструкции JS-объектов роутера на основе найденного лога
            val pattern = Pattern.compile("\"([^\"]+)\",\"\\d+\",\"\\d+\",\"([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\"")
            val matcher = pattern.matcher(html)

            var index = 0
            while (matcher.find()) {
                val deviceName = matcher.group(1
