package com.example.studynudge

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var apiKeyEdit: EditText
    private lateinit var modelEdit: EditText
    private lateinit var intervalEdit: EditText
    private lateinit var bedtimeEdit: EditText
    private lateinit var earlyEdit: EditText
    private lateinit var toggleButton: Button
    private val placeViews = HashMap<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun label(t: String, size: Float = 16f, bold: Boolean = true): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setPadding(0, dp(16f), 0, dp(4f))
        return tv
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = MaterialButton(this)
        b.text = t
        b.setOnClickListener { onClick() }
        return b
    }

    private fun edit(hintText: String, value: String, numeric: Boolean): EditText {
        val e = EditText(this)
        e.hint = hintText
        e.inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        e.setText(value)
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16f), dp(40f), dp(16f), dp(40f))
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("勉強コーチ", 26f))
        statusView = TextView(this)
        root.addView(statusView)

        // --- 権限 ---
        root.addView(label("① 権限の設定"))
        root.addView(button("他のアプリの上に表示を許可") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(button("使用状況へのアクセスを許可") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(button("位置情報・カレンダー・通知を許可") { requestRuntimePerms() })
        root.addView(button("電池の最適化の設定を開く（任意）") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(
            label(
                "※ APKを直接インストールした場合、許可の画面が押せないことがあります。その時は" +
                    "「設定 > アプリ > 勉強コーチ > 右上の︙ > 制限付き設定を許可」を先に行ってください。",
                12f, false
            )
        )

        // --- AI ---
        root.addView(label("② AI（Gemini）の設定"))
        apiKeyEdit = edit("Gemini APIキー", prefs.apiKey, false)
        root.addView(apiKeyEdit)
        modelEdit = edit("モデル名", prefs.model, false)
        root.addView(modelEdit)

        // --- 場所 ---
        root.addView(label("③ 場所の登録（地図をタップしてピンを置く）"))
        for ((key, name) in PlaceKeys.ALL) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val tv = TextView(this)
            placeViews[key] = tv
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button("地図で設定") {
                val i = Intent(this, MapPickerActivity::class.java)
                i.putExtra("key", key)
                i.putExtra("label", name)
                startActivity(i)
            })
            row.addView(button("消去") {
                prefs.clearPlace(key)
                refresh()
            })
            root.addView(row)
        }

        // --- 動作設定 ---
        root.addView(label("④ 動作の設定"))
        intervalEdit = edit("通常の声かけ間隔（分）", prefs.intervalMin.toString(), true)
        root.addView(TextView(this).apply { text = "通常の声かけ間隔（分）" })
        root.addView(intervalEdit)
        bedtimeEdit = edit("就寝時刻（時, 0-23）", prefs.bedtimeHour.toString(), true)
        root.addView(TextView(this).apply { text = "就寝時刻（この時刻以降の使用で「夜更かし」メッセージ, 0-23）" })
        root.addView(bedtimeEdit)
        earlyEdit = edit("早朝の基準（時, 0-23）", prefs.earlyHour.toString(), true)
        root.addView(TextView(this).apply { text = "早朝の基準（この時刻より前に使い始めると「おはようございます」メッセージ）" })
        root.addView(earlyEdit)
        root.addView(button("設定を保存") { saveSettings() })

        // --- 実行 ---
        root.addView(label("⑤ 実行"))
        toggleButton = button("見守りを開始") { toggleService() }
        root.addView(toggleButton)
        root.addView(button("今すぐテスト表示") { testNow() })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun requestRuntimePerms() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        list.add(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(list.toTypedArray())
    }

    private fun saveSettings() {
        prefs.apiKey = apiKeyEdit.text.toString().trim()
        val m = modelEdit.text.toString().trim()
        if (m.isNotEmpty()) prefs.model = m
        prefs.intervalMin = (intervalEdit.text.toString().toIntOrNull() ?: 30).coerceIn(5, 600)
        prefs.bedtimeHour = (bedtimeEdit.text.toString().toIntOrNull() ?: 23).coerceIn(0, 23)
        prefs.earlyHour = (earlyEdit.text.toString().toIntOrNull() ?: 5).coerceIn(0, 23)
        Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun canStart(): Boolean {
        if (!Perm.hasLocation(this)) {
            Toast.makeText(this, "先に位置情報を許可してください", Toast.LENGTH_LONG).show()
            return false
        }
        if (!Perm.hasOverlay(this)) {
            Toast.makeText(this, "先に「他のアプリの上に表示」を許可してください", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun toggleService() {
        saveSettings()
        if (NudgeService.running) {
            stopService(Intent(this, NudgeService::class.java))
        } else {
            if (!canStart()) return
            ContextCompat.startForegroundService(this, Intent(this, NudgeService::class.java))
        }
        handler.postDelayed({ refresh() }, 600L)
    }

    private fun testNow() {
        saveSettings()
        if (!canStart()) return
        val i = Intent(this, NudgeService::class.java)
        i.action = NudgeService.ACTION_TEST
        ContextCompat.startForegroundService(this, i)
        Toast.makeText(this, "ホーム画面などに移動して待ってください", Toast.LENGTH_LONG).show()
        handler.postDelayed({ refresh() }, 8000L)
    }

    private fun refresh() {
        val sb = StringBuilder()
        fun line(ok: Boolean, t: String) {
            sb.append(if (ok) "✅ " else "❌ ").append(t).append("\n")
        }
        line(Perm.hasOverlay(this), "他のアプリの上に表示")
        line(Perm.hasUsage(this), "使用状況へのアクセス")
        line(Perm.hasLocation(this), "位置情報")
        line(Perm.hasCalendar(this), "カレンダー")
        line(prefs.apiKey.isNotBlank(), "Gemini APIキー")
        line(NudgeService.running, if (NudgeService.running) "見守り中" else "停止中")
        val err = prefs.lastError
        if (err.isNotEmpty()) sb.append("\n直近のエラー: ").append(err)
        statusView.text = sb.toString()

        for ((key, name) in PlaceKeys.ALL) {
            val p = prefs.getPlace(key)
            placeViews[key]?.text = if (p == null) {
                "$name: 未設定"
            } else {
                String.format(Locale.US, "%s: %.4f, %.4f", name, p.lat, p.lng)
            }
        }
        toggleButton.text = if (NudgeService.running) "見守りを停止" else "見守りを開始"
    }
}
