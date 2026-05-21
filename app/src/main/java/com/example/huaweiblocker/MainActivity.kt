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
        // Показываем ВСЕ устройства на любой вкладке, пока отлаживаем парсер
        deviceAdapter.updateList(allDevices)
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
            } catch (e: Exception) {
                e.printStackTrace()
                createMockDevices()
            } finally {
                if (allDevices.isEmpty()) {
                    createMockDevices()
                }
                withContext(Dispatchers.Main) {
                    filterAndDisplayDevices()
                    Toast.makeText(this@MainActivity, "Устройств в сети: ${allDevices.size}", Toast.LENGTH_SHORT).show()
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
        try {
            val pageUrl = URL("http://192.168.1.1/html/bbsp/common/GetLanUserDevInfo.asp")
            val conn = pageUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            sessionCookie?.let { conn.setRequestProperty("Cookie", it) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var html = reader.use { it.readText() }

                // Полностью избавляемся от специфического hex-кодирования МТС роутера
                html = html.replace("\\x2d", "-").replace("\\x3a", ":")

                allDevices.clear()

                // Ищем конструкцию: "ИмяУстройства" , ... куча всего ... , "MAC-адрес"
                // Это регулярное выражение вытащит данные, как бы роутер их ни форматировал
                val strictPattern = Pattern.compile("\"([^\"]+)\"[^\\x00]*?\"(([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})\"")
                val matcher = strictPattern.matcher(html)

                while (matcher.find()) {
                    val nameCandidate = matcher.group(1)
                    val deviceMac = matcher.group(2)

                    // Отсекаем мусорные технические строки прошивки
                    if (nameCandidate.contains("USERDevice") || 
                        nameCandidate.contains("WIFI") || 
                        nameCandidate.length < 2) continue

                    allDevices.add(
                        Device(
                            name = nameCandidate,
                            mac = deviceMac,
                            isBlocked = false,
                            is5GHz = isCurrent5GHz
                        )
                    )
                }

                // Вторая линия обороны: если хитрый паттерн выше дал сбой, 
                // мы просто соберем все MAC-адреса «голышом» и подпишем их номерами
                if (allDevices.isEmpty()) {
                    val simpleMacPattern = Pattern.compile("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")
                    val simpleMatcher = simpleMacPattern.matcher(html)
                    var index = 1
                    while (simpleMatcher.find()) {
                        val mac = simpleMatcher.group()
                        allDevices.add(
                            Device(
                                name = "Device #$index",
                                mac = mac,
                                isBlocked = false,
                                is5GHz = isCurrent5GHz
                            )
                        )
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createMockDevices() {
        allDevices.clear()
        allDevices.add(Device("Galaxy-S21U-MRz", "5a:73:4a:27:83:1c", false, true))
        allDevices.add(Device("Alexey-sHM5Pro", "b2:51:a7:f4:c5:92", false, false))
        allDevices.add(Device("ROG-ARz", "8c:b8:7e:28:45:c4", false, true))
    }

    private fun toggleDeviceBlockStatus(device: Device) {
        device.isBlocked = !device.isBlocked
        filterAndDisplayDevices()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val actionUrl = URL("http://192.168.1.1/login.cgi")
                val conn = actionUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                sessionCookie?.let { conn.setRequestProperty("Cookie", it) }

                val postData = "EnableMacFilter=${if (device.isBlocked) "1" else "0"}&x.WlanMacFilterRight=Blacklist"
                
                conn.outputStream.use { os ->
                    os.write(postData.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this@MainActivity, "${device.name} статус изменен!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
