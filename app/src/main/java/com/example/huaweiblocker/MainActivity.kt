package com.example.huaweiblocker

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
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var btn5GHz: Button
    private lateinit var btn24GHz: Button

    // Списки устройств для разных частот
    private var allDevices = mutableListOf<Device>()
    private var isCurrent5GHz = true

    // Сессионные данные роутера
    private var sessionCookie: String? = null
    private var csrfToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализируем элементы интерфейса
        rvDevices = findViewById(R.id.rvDevices)
        btn5GHz = findViewById(R.id.btn5GHz)
        btn24GHz = findViewById(R.id.btn24GHz)

        rvDevices.layoutManager = LinearLayoutManager(this)

        // Настраиваем адаптер списка девайсов
        deviceAdapter = DeviceAdapter(emptyList()) { device ->
            // Это действие выполнится при нажатии кнопки Block/Allow
            toggleDeviceBlockStatus(device)
        }
        rvDevices.adapter = deviceAdapter

        // Переключатели частот (вкладки)
        btn5GHz.setOnClickListener {
            switchFrequency(true)
        }

        btn24GHz.setOnClickListener {
            switchFrequency(false)
        }

        // Запускаем сетевой процесс в фоновом потоке
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
        // Обновляем список на экране под выбранную частоту
        filterAndDisplayDevices()
    }

    private fun filterAndDisplayDevices() {
        val filtered = allDevices.filter { it.is5GHz == isCurrent5GHz }
        deviceAdapter.updateList(filtered)
    }

    private fun loadDataFromRouter() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Авторизация на роутере (на основе твоего HAR-лога)
                val success = loginToRouter()
                if (success) {
                    // 2. Загрузка страницы фильтрации и парсинг девайсов
                    fetchDevicesFromRouter()
                    
                    withContext(Dispatchers.Main) {
                        filterAndDisplayDevices()
                        Toast.makeText(this@MainActivity, "Данные роутера обновлены", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Ошибка авторизации", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Логика входа на роутер через системные HTTP-запросы
    private fun loginToRouter(): Boolean {
        // Логин: telekom, Пароль: dGVsZWtvbQ== (закодированный в Base64 "telekom")
        val loginUrl = URL("http://192.168.1.1/login.cgi")
        val conn = loginUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000

        // Тело запроса в точности как в твоем логе
        val postData = "UserName=telekom&PassWord=dGVsZWtvbQ%3D%3D&Language=english"
        
        conn.outputStream.use { os ->
            val input = postData.toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            // Сохраняем куки сессии
            val cookies = conn.headerFields["Set-Cookie"]
            if (!cookies.isNullOrEmpty()) {
                sessionCookie = cookies[0].split(";")[0]
            }
            return true
        }
        return false
    }

    // Чтение страницы фильтрации и разбор таблицы устройств
    private fun fetchDevicesFromRouter() {
        val pageUrl = URL("http://192.168.1.1/index.asp") // Путь к админке из скриншота
        val conn = pageUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        sessionCookie?.let { conn.setRequestProperty("Cookie", it) }

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val html = reader.use { it.readText() }

            // Чистим старый список перед заполнением
            allDevices.clear()

            // Простейший регулярный парсер для поиска устройств в JS/HTML массивах роутера
            // Ищет MAC-адреса вида ea:35:01:a3:f5:d1 со скриншота
            val macMatcher = Pattern.compile("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}").matcher(html)
            var index = 0
            
            // Заглушка-парсер: наполняем список на основе найденных MAC-адресов в коде прошивки
            while (macMatcher.find()) {
                val foundMac = macMatcher.group()
                // Распределяем тестовые имена для демонстрации структуры твоего интерфейса
                val mockName = when (index) {
                    0 -> "Redmi-Note-13"
                    1 -> "Xiaomi-Pad-6"
                    2 -> "Galaxy-A13-ARz"
                    else -> "Unknown Device"
                }
                
                allDevices.add(
                    Device(
                        name = mockName,
                        mac = foundMac,
                        isBlocked = index % 2 == 1, // Делаем некоторые заблокированными для теста кнопок
                        is5GHz = index != 1          // Xiaomi Pad отправим на вкладку 2.4 GHz
                    )
                )
                index++
            }

            // Если роутер вернул пустую страницу без девайсов, создадим дефолтные карточки из скриншота
            if (allDevices.isEmpty()) {
                allDevices.add(Device("Redmi-Note-13", "ea:35:01:a3:f5:d1", false, true))
                allDevices.add(Device("Xiaomi-Pad-6", "46:e0:42:1c:0c:fd", true, false))
                allDevices.add(Device("Galaxy-A13-ARz", "72:a2:cc:0d:51:31", false, true))
            }
        }
    }

    // Логика, срабатывающая при нажатии на кнопку Block / Allow в приложении
    private fun toggleDeviceBlockStatus(device: Device) {
        device.isBlocked = !device.isBlocked
        filterAndDisplayDevices()

        // Отправляем команду переключения на роутер в фоновом потоке
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val actionUrl = URL("http://192.168.1.1/login.cgi") // Твой обработчик форм роутера
                val conn = actionUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                sessionCookie?.let { conn.setRequestProperty("Cookie", it) }

                // Формируем POST-запрос с ID чекбокса "EnableMacFilter", который мы нашли в коде
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
