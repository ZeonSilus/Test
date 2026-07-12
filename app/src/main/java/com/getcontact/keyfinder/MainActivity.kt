package com.getcontact.keyfinder

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

        btnSearch.setOnClickListener { searchForKeys() }
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
            Нажмите "Поиск ключей" для поиска TOKEN и FINAL_KEY.
            
            Работает только на устройствах с ROOT.
        """.trimIndent()
    }

    private fun searchForKeys() {
        progressBar.visibility = android.view.View.VISIBLE
        tvStatus.text = "Поиск ключей..."
        tvToken.text = "..."
        tvFinalKey.text = "..."
        tvInstructions.text = ""

        Thread {
            val result = findGetContactKeys()
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                displayResults(result)
            }
        }.start()
    }

    private fun execSu(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val content = reader.readText()
        process.waitFor()
        return content
    }

    private fun findGetContactKeys(): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        result["token"] = null
        result["finalKey"] = null
        result["found"] = "false"
        result["path"] = ""
        result["raw"] = ""

        // Все варианты имён пакетов
        val packages = listOf(
            "app.source.getcontact",
            "com.getcontact.android",
            "com.getcontact"
        )

        // Все варианты имён файлов
        val prefFiles = listOf(
            "GetContactSettingsPref.xml",
            "settings.xml",
            "prefs.xml",
            "config.xml"
        )

        for (pkg in packages) {
            for (prefFile in prefFiles) {
                try {
                    val path = "/data/data/$pkg/shared_prefs/$prefFile"
                    val content = execSu("cat $path 2>/dev/null")

                    if (content.isNotEmpty() && !content.contains("No such file") && !content.contains("Permission denied")) {
                        result["raw"] = content
                        result["path"] = path

                        // Пробуем найти ключи разными способами
                        val keys = parseKeys(content)
                        if (keys["token"] != null || keys["finalKey"] != null) {
                            result["token"] = keys["token"]
                            result["finalKey"] = keys["finalKey"]
                            result["found"] = "true"
                            return result
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Также пробуем найти через ls все XML файлы
        try {
            for (pkg in packages) {
                val lsOutput = execSu("ls /data/data/$pkg/shared_prefs/ 2>/dev/null")
                if (lsOutput.isNotEmpty() && !lsOutput.contains("No such file")) {
                    val files = lsOutput.lines().filter { it.endsWith(".xml") }
                    for (file in files) {
                        try {
                            val path = "/data/data/$pkg/shared_prefs/$file"
                            val content = execSu("cat $path 2>/dev/null")
                            if (content.isNotEmpty()) {
                                val keys = parseKeys(content)
                                if (keys["token"] != null || keys["finalKey"] != null) {
                                    result["token"] = keys["token"]
                                    result["finalKey"] = keys["finalKey"]
                                    result["found"] = "true"
                                    result["path"] = path
                                    result["raw"] = content
                                    return result
                                }
                                // Если нашли хотя бы какой-то файл, сохраняем
                                if (result["raw"].isNullOrEmpty()) {
                                    result["raw"] = content
                                    result["path"] = path
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    private fun parseKeys(xmlContent: String): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        result["token"] = null
        result["finalKey"] = null

        // Android SharedPreferences XML format:
        // <map>
        //   <string name="TOKEN">value</string>
        //   <string name="FINAL_KEY">value</string>
        // </map>

        val keysToFind = mapOf(
            "token" to listOf("TOKEN", "CHAT_TOKEN", "token", "chat_token"),
            "finalKey" to listOf("FINAL_KEY", "final_key", "finalKey")
        )

        for ((resultKey, names) in keysToFind) {
            for (name in names) {
                // Pattern 1: <string name="KEY">VALUE</string>
                var regex = Regex("""<string\s+name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                var matchResult = regex.find(xmlContent)
                if (matchResult != null && matchResult.groupValues[1].isNotEmpty()) {
                    result[resultKey] = matchResult.groupValues[1]
                    break
                }

                // Pattern 2: <string name="KEY" value="VALUE"/>
                regex = Regex("""<string\s+name="$name"\s+value="(.*?)"/>""")
                matchResult = regex.find(xmlContent)
                if (matchResult != null && matchResult.groupValues[1].isNotEmpty()) {
                    result[resultKey] = matchResult.groupValues[1]
                    break
                }

                // Pattern 3: name="KEY" value="VALUE"
                regex = Regex("""name="$name"[^>]*value="(.*?)"|value="(.*?)"[^>]*name="$name"""")
                val m3 = regex.find(xmlContent)
                if (m3 != null) {
                    val value = if (m3.groupValues[1].isNotEmpty()) m3.groupValues[1] else m3.groupValues[2]
                    if (value.isNotEmpty()) {
                        result[resultKey] = value
                        break
                    }
                }

                // Pattern 4: Any JSON-like format
                regex = Regex("""name="$name"[^"]*"[^"]*?"(.*?)"|"(.*?)"[^"]*name="$name"""")
                val m4 = regex.find(xmlContent)
                if (m4 != null) {
                    val value = if (m4.groupValues[1].isNotEmpty()) m4.groupValues[1] else m4.groupValues[2]
                    if (value.isNotEmpty()) {
                        result[resultKey] = value
                        break
                    }
                }
            }
        }

        return result
    }

    private fun displayResults(result: Map<String, String?>) {
        if (result["found"] == "true") {
            tvStatus.text = "Ключи найдены!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvToken.text = result["token"] ?: "Не найден"
            tvFinalKey.text = result["finalKey"] ?: "Не найден"
            Toast.makeText(this, "Файл: ${result["path"]}", Toast.LENGTH_LONG).show()
        } else if (!(result["raw"]).isNullOrEmpty()) {
            // Файл найден, но ключи не распознаны — покажем raw
            tvStatus.text = "Файл найден, но ключи не распознаны"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            tvToken.text = "см. инструкции"
            tvFinalKey.text = "см. инструкции"

            // Показываем содержимое файла для ручного поиска
            val raw = result["raw"] ?: ""
            val truncated = if (raw.length > 1500) raw.substring(0, 1500) + "..." else raw
            tvInstructions.text = """
                Файл: ${result["path"]}
                
                Содержимое (ищите TOKEN и FINAL_KEY):
                
                $truncated
            """.trimIndent()
        } else {
            tvStatus.text = "Ключи не найдены"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvToken.text = "Не найден"
            tvFinalKey.text = "Не найден"
            tvInstructions.text = "Убедитесь что GetContact установлен и ROOT доступен"
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label скопирован!", Toast.LENGTH_SHORT).show()
    }
}