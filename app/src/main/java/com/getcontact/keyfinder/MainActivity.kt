package com.getcontact.keyfinder

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
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
        tvStatus.text = "Готов к поиску"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        tvInstructions.text = "Нажмите \"Поиск ключей\""
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

        btnSearch.setOnClickListener { searchForKeys() }
        btnCopyToken.setOnClickListener {
            val v = tvToken.text.toString()
            if (v.isNotEmpty() && v != "..." && v != "Не найден") copy("TOKEN", v)
        }
        btnCopyKey.setOnClickListener {
            val v = tvFinalKey.text.toString()
            if (v.isNotEmpty() && v != "..." && v != "Не найден") copy("FINAL_KEY/UID", v)
        }
    }

    private fun searchForKeys() {
        progressBar.visibility = android.view.View.VISIBLE
        tvStatus.text = "Поиск..."
        tvToken.text = "..."
        tvFinalKey.text = "..."
        tvInstructions.text = ""

        Thread {
            val packages = listOf("app.source.getcontact", "com.getcontact.android", "com.getcontact")
            var xmlContent = ""
            var foundPath = ""

            // Ищем XML с BASIC_INIT_PARAMS_KEY
            for (pkg in packages) {
                try {
                    val ls = execSu("ls /data/data/$pkg/shared_prefs/ 2>/dev/null")
                    if (ls.isBlank() || ls.contains("No such file")) continue

                    val files = ls.lines().filter { it.endsWith(".xml") && it.isNotBlank() }
                    for (file in files) {
                        try {
                            val path = "/data/data/$pkg/shared_prefs/$file"
                            val content = execSu("cat $path 2>/dev/null")
                            if (content.contains("BASIC_INIT_PARAMS_KEY")) {
                                xmlContent = content
                                foundPath = "$pkg/$file"
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    if (xmlContent.isNotEmpty()) break
                } catch (_: Exception) {}
            }

            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                if (xmlContent.isEmpty()) {
                    tvStatus.text = "GetContact не найден"
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    tvInstructions.text = "Установите GetContact и войдите в аккаунт"
                } else {
                    parseAndDisplay(xmlContent, foundPath)
                }
            }
        }.start()
    }

    private fun parseAndDisplay(xml: String, path: String) {
        try {
            // Извлекаем JSON из BASIC_INIT_PARAMS_KEY
            val jsonMatch = Regex("""<string name="BASIC_INIT_PARAMS_KEY">(.*?)</string>""").find(xml)
            if (jsonMatch == null) {
                tvStatus.text = "BASIC_INIT_PARAMS_KEY не найден"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                return
            }

            // Декодируем HTML entities (&quot; -> ")
            val jsonStr = jsonMatch.groupValues[1].replace("&quot;", "\"")
            val json = JSONObject(jsonStr)

            val token = json.optString("token", "")
            val uid = json.optString("uId", "")
            val serverKey = json.optString("serverKey", "")
            val safetyToken = json.optString("safetyToken", "")

            // Также ищем NOTIF_TOKEN_KEY
            val notifMatch = Regex("""<string name="NOTIF_TOKEN_KEY">(.*?)</string>""").find(xml)
            val notifToken = notifMatch?.groupValues?.get(1) ?: ""

            // Также ищем UNIQUE_ID
            val uniqueMatch = Regex("""<string name="BASIC_INIT_UNIQUE_ID_KEY">(.*?)</string>""").find(xml)
            val uniqueId = uniqueMatch?.groupValues?.get(1) ?: ""

            if (token.isNotEmpty()) {
                tvStatus.text = "Ключи найдены!"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                tvToken.text = token
                tvFinalKey.text = uid
            } else {
                tvStatus.text = "Токен пуст"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                tvToken.text = "Не найден"
                tvFinalKey.text = "Не найден"
            }

            // Показываем все найденные данные
            val sb = StringBuilder()
            sb.appendLine("Файл: $path")
            sb.appendLine()
            sb.appendLine("=== ОСНОВНЫЕ КЛЮЧИ ===")
            sb.appendLine("TOKEN:       $token")
            sb.appendLine("UID:         $uid")
            sb.appendLine("Server Key:  $serverKey")
            sb.appendLine("Safety Tok:  $safetyToken")
            sb.appendLine("Unique ID:   $uniqueId")
            sb.appendLine("Notif Token: ${notifToken.take(50)}...")
            sb.appendLine()
            sb.appendLine("=== ПОЛНЫЙ JSON ===")
            sb.appendLine(json.toString(2))

            tvInstructions.text = sb.toString()

        } catch (e: Exception) {
            tvStatus.text = "Ошибка парсинга"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvInstructions.text = "Ошибка: ${e.message}"
        }
    }

    private fun execSu(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val text = r.readText()
            p.waitFor()
            text
        } catch (e: Exception) {
            ""
        }
    }

    private fun copy(label: String, text: String) {
        val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label скопирован!", Toast.LENGTH_SHORT).show()
    }
}