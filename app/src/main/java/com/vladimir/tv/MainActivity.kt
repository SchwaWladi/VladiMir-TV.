package com.vladimir.tv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var channels: Spinner
    private val entries = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(12, 8, 12, 8)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Ссылка на M3U или видеопоток (http/https)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.DKGRAY)
            setText(getPreferences(MODE_PRIVATE).getString("last_url", ""))
        }
        top.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val load = Button(this).apply { text = "Открыть" }
        top.addView(load)
        layout.addView(top)
        channels = Spinner(this)
        layout.addView(channels)
        status = TextView(this).apply { text = "Введите ссылку и нажмите Открыть"; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL }
        layout.addView(status)
        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
            isFocusable = true
        }
        layout.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        load.setOnClickListener { openInput() }
        channels.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in entries.indices) play(entries[position].second)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                status.text = "Ошибка воспроизведения: ${error.errorCodeName}"
            }
        })
        load.requestFocus()
    }

    private fun openInput() {
        val address = input.text.toString().trim()
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            status.text = "Нужна ссылка http:// или https://"
            return
        }
        getPreferences(MODE_PRIVATE).edit().putString("last_url", address).apply()
        if (address.substringBefore('?').endsWith(".m3u", true) || address.contains("type=m3u", true)) {
            loadPlaylist(address)
        } else {
            // HLS .m3u8 is a stream; other URLs are passed to the player directly.
            entries.clear()
            channels.adapter = null
            play(address)
        }
    }

    private fun loadPlaylist(address: String) {
        status.text = "Загрузка плейлиста…"
        thread {
            try {
                val conn = URL(address).openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                val content = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val parsed = mutableListOf<Pair<String, String>>()
                var title = "Канал"
                for (raw in content.lineSequence()) {
                    val line = raw.trim()
                    if (line.startsWith("#EXTINF", true)) title = line.substringAfterLast(',', "Канал").trim().ifEmpty { "Канал" }
                    else if (line.startsWith("http://") || line.startsWith("https://")) {
                        parsed.add(title to line)
                        title = "Канал"
                    }
                }
                runOnUiThread {
                    entries.clear(); entries.addAll(parsed)
                    channels.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, entries.map { it.first })
                    status.text = if (entries.isEmpty()) "В плейлисте не найдены каналы" else "Каналов: ${entries.size}. Выберите канал."
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Не удалось загрузить плейлист: ${e.message}" }
            }
        }
    }

    private fun play(address: String) {
        try {
            player.setMediaItem(MediaItem.fromUri(address))
            player.prepare()
            player.playWhenReady = true
            status.text = "Воспроизведение…"
        } catch (e: Exception) {
            status.text = "Ошибка: ${e.message}"
        }
    }

    override fun onDestroy() {
        playerView.player = null
        player.release()
        super.onDestroy()
    }
}
