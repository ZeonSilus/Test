package com.getcontact.keyfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        checkPermissions()
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
            if (token.isNotEmpty() && token != "Не найден") {
                copyToClipboard("TOKEN", token)
            }
        }

        btnCopyKey.setOnClickListener {
            val key = tvFinalKey.text.toString()
            if (key.isNotEmpty() && key != "Не найден") {
                copyToClipboard("FINAL_KEY", key)
            }
        }

        showInstructions()
    }

    private fun showInstructions() {
        tvInstructions.text = """
            Инструкция:
            
            1. Установите и откройте GetContact
            2. Войдите в аккаунт
            3. Нажмите "Поиск ключей"
            
            ⚠️ Требуется ROOT или ADB доступ
            
            Варианты получения ключей:
            • Через ROOT: приложение найдёт файл автоматически
            • Через ADB: подключите телефон к ПК и выполните:
              adb pull /data/data/app.source.getcontact/shared_prefs/
        """.trimIndent()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Нужны разрешения для доступа к файлам", Toast.LENGTH_LONG).show()
            }
        }
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

        // Список возможных путей к файлу
        val possiblePaths = listOf(
            // ROOT пути
            "/data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml",
            "/data/user/0/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml",
            "/data/data/com.getcontact.android/shared_prefs/GetContactSettingsPref.xml",
            "/data/user/0/com.getcontact.android/shared_prefs/GetContactSettingsPref.xml",
            // SD карта и общее хранилище
            "${Environment.getExternalStorageDirectory()}/Android/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml",
            "${Environment.getExternalStorageDirectory()}/Download/GetContactSettingsPref.xml",
            "${Environment.getExternalStorageDirectory()}/GetContactSettingsPref.xml"
        )

        for (path in possiblePaths) {
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
        }

        // Попытка через Runtime.exec (если есть ROOT)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val content = reader.readText()
            process.waitFor()

            if (content.isNotEmpty()) {
                val token = extractValue(content, "TOKEN")
                val finalKey = extractValue(content, "FINAL_KEY")
                val chatToken = extractValue(content, "CHAT_TOKEN")

                result["token"] = token ?: chatToken
                result["finalKey"] = finalKey
                result["found"] = "true"
                result["path"] = "ROOT: /data/data/app.source.getcontact/shared_prefs/"
            }
        } catch (e: Exception) {
            // ROOT недоступен
        }

        return result
    }

    private fun extractValue(xmlContent: String, key: String): String? {
        // Парсинг XML вручную (без зависимостей)
        val patterns = listOf(
            // Standard Android SharedPreferences format
            """name="$key"[^>]*>([^<]+)<""",
            """name="$key"\s+value="([^"]+)"""",
            // JSON format
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
            tvStatus.text = "✓ Ключи найдены!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

            tvToken.text = result["token"] ?: "Не найден"
            tvFinalKey.text = result["finalKey"] ?: "Не найден"

            Toast.makeText(this, "Файл: ${result["path"]}", Toast.LENGTH_LONG).show()
        } else {
            tvStatus.text = "✗ Ключи не найдены"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvToken.text = "Не найден"
            tvFinalKey.text = "Не найден"

            showNoRootInstructions()
        }
    }

    private fun showNoRootInstructions() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("ROOT не найден")
            .setMessage("""
                Для работы приложения нужен ROOT-доступ.
                
                Альтернативы:
                
                1. Через ADB (компьютер):
                   adb pull /data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml
                
                2. Установите Magisk для ROOT
                
                3. Используйте эмулятор Android Studio
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