package com.getcontact.keyfinder

import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvToken: TextView
    private lateinit var tvFinalKey: TextView
    private lateinit var btnSearch: Button
    private lateinit var btnCopyToken: Button
    private lateinit var btnCopyKey: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInstructions: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        showInstructions()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvToken = findViewById(R.id.tvToken)
        tvFinalKey = findViewById(R.id.tvFinalKey)
        btnSearch = findViewById(R.id.btnSearch)
        btnCopyToken = findViewById(R.id.btnCopyToken)
        btnCopyKey = findViewById(R.id.btnCopyKey)
        progressBar = findViewById(R.id.progressBar)
        tvInstructions = findViewById(R.id.tvInstructions)

        btnSearch.setOnClickListener {
            searchForKeys()
        }

        btnCopyToken.setOnClickListener {
            val token = tvToken.text.toString()
            if (token.isNotEmpty() && token != "Не найден" && token != "...") {
                copyToClipboard("TOKEN", token)
            }
        }

        btnCopyKey.setOnClickListener {
            val key = tvFinalKey.text.toString()
            if (key.isNotEmpty() && key != "Не найден" && key != "...") {
                copyToClipboard("FINAL_KEY", key)
            }
        }
    }

    private fun showInstructions() {
        tvStatus.text = "Готов к поиску"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))

        tvInstructions.text = """
            Как получить ключи GetContact:
            
            Способ 1: Через ADB (рекомендуется)
            1. Включите "Отладка USB" на телефоне
            2. Подключите телефон к компьютеру
            3. В терминале выполните:
               adb pull /data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml
            4. Откройте скачанный файл и найдите TOKEN и FINAL_KEY
            
            Способ 2: ROOT (если есть)
            1. Нажмите "Поиск ключей"
            2. Приложение запросит ROOT-доступ
            3. Разрешите и ключи найдутся автоматически
            
            Способ 3: Эмулятор
            1. Установите Android Studio
            2. Создайте виртуальное устройство
            3. Установите GetContact и войдите в аккаунт
            4. Используйте ADB для извлечения ключей
        """.trimIndent()
    }

    private fun searchForKeys() {
        progressBar.visibility = android.view.View.VISIBLE
        tvStatus.text = "Поиск ключей..."
        tvToken.text = "..."
        tvFinalKey.text = "..."

        Thread {
            val result = findGetContactKeys()
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                displayResults(result)
            }
        }.start()
    }

    private fun findGetContactKeys(): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        result["token"] = null
        result["finalKey"] = null
        result["found"] = "false"
        result["path"] = ""

        // Попытка через ROOT (su command)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val content = reader.readText()
            val error = errorReader.readText()
            process.waitFor()

            if (content.isNotEmpty() && !content.contains("Permission denied")) {
                val token = extractValue(content, "TOKEN")
                val finalKey = extractValue(content, "FINAL_KEY")
                val chatToken = extractValue(content, "CHAT_TOKEN")

                result["token"] = token ?: chatToken
                result["finalKey"] = finalKey
                result["found"] = "true"
                result["path"] = "ROOT: /data/data/app.source.getcontact/shared_prefs/"
                return result
            }
        } catch (e: Exception) {
            // ROOT недоступен или не установлен
        }

        // Попытка прямого доступа (только если приложение имеет права)
        val possiblePaths = listOf(
            "/data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml",
            "/data/user/0/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml",
            "/data/data/com.getcontact.android/shared_prefs/GetContactSettingsPref.xml",
            "/data/user/0/com.getcontact.android/shared_prefs/GetContactSettingsPref.xml"
        )

        for (path in possiblePaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText()
                    val token = extractValue(content, "TOKEN")
                    val finalKey = extractValue(content, "FINAL_KEY")
                    val chatToken = extractValue(content, "CHAT_TOKEN")

                    if (token != null || finalKey != null) {
                        result["token"] = token ?: chatToken
                        result["finalKey"] = finalKey
                        result["found"] = "true"
                        result["path"] = path
                        return result
                    }
                }
            } catch (e: Exception) {
                // Пропускаем этот путь
            }
        }

        return result
    }

    private fun extractValue(xmlContent: String, key: String): String? {
        val patterns = listOf(
            """name="$key"[^>]*>([^<]+)<""",
            """name="$key"\s+value="([^"]+)"""",
            """\"$key\"\s*:\s*\"([^\"]+)\""""
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(xmlContent)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun displayResults(result: Map<String, String?>) {
        if (result["found"] == "true") {
            tvStatus.text = "Ключи найдены!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

            tvToken.text = result["token"] ?: "Не найден"
            tvFinalKey.text = result["finalKey"] ?: "Не найден"

            Toast.makeText(this, "Источник: ${result["path"]}", Toast.LENGTH_LONG).show()
        } else {
            tvStatus.text = "Ключи не найдены (нужен ROOT или ADB)"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvToken.text = "Не найден"
            tvFinalKey.text = "Не найден"

            showNoRootDialog()
        }
    }

    private fun showNoRootDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("ROOT не найден")
            .setMessage("""
                Без ROOT приложение не может прочитать файлы GetContact.
                
                Используйте ADB:
                
                1. Включите "Отладка USB" в настройках разработчика
                2. Подключите телефон к компьютеру
                3. Выполните команду:
                
                adb pull /data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml
                
                4. Откройте файл и найдите значения TOKEN и FINAL_KEY
            """.trimIndent())
            .setPositiveButton("Понятно", null)
            .setNeutraliative("Как включить отладку USB?") { _, _ ->
                showUsbDebugInstructions()
            }
            .create()
        dialog.show()
    }

    private fun showUsbDebugInstructions() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Включение отладки USB")
            .setMessage("""
                1. Откройте "Настройки"
                2. Перейдите в "О телефоне"
                3. Нажмите 7 раз на "Номер сборки" (Build Number)
                4. Вернитесь в "Настройки"
                5. Откройте "Для разработчиков"
                6. Включите "Отладка USB"
            """.trimIndent())
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label скопирован!", Toast.LENGTH_SHORT).show()
    }
}