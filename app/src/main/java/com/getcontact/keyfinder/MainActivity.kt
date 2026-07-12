package com.getcontact.keyfinder

import android.content.ClipData
import android.content.ClipboardManager
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
            if (v.isNotEmpty() && v != "..." && v != "Не найден") copy("FINAL_KEY", v)
        }
    }

    private fun searchForKeys() {
        progressBar.visibility = android.view.View.VISIBLE
        tvStatus.text = "Поиск..."
        tvToken.text = "..."
        tvFinalKey.text = "..."
        tvInstructions.text = ""

        Thread {
            val allFiles = mutableMapOf<String, String>()
            val packages = listOf("app.source.getcontact", "com.getcontact.android", "com.getcontact")

            // Читаем ВСЕ XML файлы из shared_prefs ВСЕХ пакетов
            for (pkg in packages) {
                try {
                    val ls = execSu("ls /data/data/$pkg/shared_prefs/ 2>/dev/null")
                    if (ls.isBlank() || ls.contains("No such file")) continue

                    val files = ls.lines().filter { it.endsWith(".xml") && it.isNotBlank() }
                    for (file in files) {
                        try {
                            val path = "/data/data/$pkg/shared_prefs/$file"
                            val content = execSu("cat $path 2>/dev/null")
                            if (content.isNotBlank() && !content.contains("Permission denied")) {
                                allFiles["$pkg/$file"] = content
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                if (allFiles.isEmpty()) {
                    tvStatus.text = "Файлы не найдены"
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    tvInstructions.text = "Убедитесь что GetContact установлен и ROOT доступен"
                } else {
                    displayAllFiles(allFiles)
                }
            }
        }.start()
    }

    private fun displayAllFiles(files: Map<String, String>) {
        // Ищем ключи во всех файлах
        var foundToken: String? = null
        var foundKey: String? = null
        var foundFile = ""

        // Все возможные имена для TOKEN
        val tokenNames = listOf(
            "TOKEN", "CHAT_TOKEN", "token", "chat_token",
            "auth_token", "access_token", "session_token",
            "TOKEN_KEY", "api_token", "user_token"
        )
        // Все возможные имена для FINAL_KEY
        val keyNames = listOf(
            "FINAL_KEY", "final_key", "finalKey",
            "FINAL_KEY_ID", "encryption_key", "secret_key",
            "KEY_FINAL", "private_key", "aes_key"
        )

        for ((fileName, content) in files) {
            // Ищем TOKEN
            if (foundToken == null) {
                for (name in tokenNames) {
                    val value = extractXmlString(content, name)
                    if (value != null && value.length > 5) {
                        foundToken = value
                        foundFile = fileName
                        break
                    }
                }
            }
            // Ищем FINAL_KEY
            if (foundKey == null) {
                for (name in keyNames) {
                    val value = extractXmlString(content, name)
                    if (value != null && value.length > 3) {
                        foundKey = value
                        if (foundFile.isEmpty()) foundFile = fileName
                        break
                    }
                }
            }
        }

        if (foundToken != null || foundKey != null) {
            tvStatus.text = "Ключи найдены!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvToken.text = foundToken ?: "Не найден"
            tvFinalKey.text = foundKey ?: "Не найден"
        } else {
            tvStatus.text = "TOKEN/FINAL_KEY не найдены"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            tvToken.text = "Не найден"
            tvFinalKey.text = "Не найден"
        }

        // Показываем ВСЕ файлы и их содержимое
        val sb = StringBuilder()
        for ((fileName, content) in files) {
            sb.appendLine("=== $fileName ===")
            // Только строки с name= (ключевые пары)
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.contains("name=") && (trimmed.contains("<string") || trimmed.contains("<boolean") || trimmed.contains("<int") || trimmed.contains("<long"))) {
                    sb.appendLine(trimmed)
                }
            }
            sb.appendLine()
        }

        val text = sb.toString()
        val truncated = if (text.length > 4000) text.substring(0, 4000) + "\n...ещё" else text
        tvInstructions.text = truncated
    }

    private fun extractXmlString(xml: String, name: String): String? {
        // <string name="KEY">VALUE</string>
        val r1 = Regex("""<string\s+name="$name">(.*?)</string>""")
        r1.find(xml)?.let { if (it.groupValues[1].isNotEmpty()) return it.groupValues[1] }

        // <string name="KEY" value="VALUE"/>
        val r2 = Regex("""<string\s+name="$name"\s+value="(.*?)"/>""")
        r2.find(xml)?.let { if (it.groupValues[1].isNotEmpty()) return it.groupValues[1] }

        return null
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