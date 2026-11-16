package com.framestudio.android

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var selectedVideo: Uri? = null
    private var transformer: Transformer? = null
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var exportButton: Button

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            selectedVideo = uri
            loadVideo(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildEditorUi()
    }

    private fun buildEditorUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(0xFF101018.toInt())
        }
        val title = TextView(this).apply {
            text = "FrameStudio"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "Buat video dengan gayamu."
            textSize = 14f
            setTextColor(0xFFAAA7B8.toInt())
        }
        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(0xFF050508.toInt())
        }
        val choose = Button(this).apply {
            text = "Pilih video"
            setOnClickListener { videoPicker.launch(arrayOf("video/*")) }
        }
        val trimTitle = TextView(this).apply {
            text = "POTONG VIDEO  •  waktu dalam detik"
            textSize = 12f
            setTextColor(0xFFBDB5D5.toInt())
            setPadding(0, dp(16), 0, dp(6))
        }
        val inputs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startInput = EditText(this).apply {
            hint = "Mulai (0)"
            inputType = 8194
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888494.toInt())
            setSingleLine(true)
        }
        endInput = EditText(this).apply {
            hint = "Akhir (durasi)"
            inputType = 8194
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888494.toInt())
            setSingleLine(true)
        }
        inputs.addView(startInput, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
        inputs.addView(endInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        exportButton = Button(this).apply {
            text = "Ekspor MP4"
            isEnabled = false
            setOnClickListener { exportVideo() }
        }
        status = TextView(this).apply {
            text = "Pilih video untuk mulai mengedit."
            textSize = 13f
            setTextColor(0xFFBDB5D5.toInt())
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(18); bottomMargin = dp(12)
        })
        root.addView(choose, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(trimTitle)
        root.addView(inputs)
        root.addView(exportButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        root.addView(status)
        setContentView(root)
    }

    private fun loadVideo(uri: Uri) {
        if (player == null) {
            player = ExoPlayer.Builder(this).build().also { playerView.player = it }
        }
        player?.setMediaItem(MediaItem.fromUri(uri))
        player?.prepare()
        player?.playWhenReady = false
        status.text = "Video siap. Atur waktu mulai dan akhir, lalu ekspor."
        exportButton.isEnabled = true
        endInput.setText("")
    }

    private fun exportVideo() {
        val uri = selectedVideo ?: return
        val startMs = ((startInput.text.toString().toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceAtLeast(0)
        val endSeconds = endInput.text.toString().toDoubleOrNull()
        val builder = MediaItem.Builder().setUri(uri)
        if (endSeconds != null) {
            val endMs = (endSeconds * 1000).toLong()
            if (endMs <= startMs) {
                Toast.makeText(this, "Waktu akhir harus lebih besar dari waktu mulai.", Toast.LENGTH_LONG).show()
                return
            }
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
        } else if (startMs > 0) {
            Toast.makeText(this, "Isi waktu akhir untuk menggunakan fitur potong.", Toast.LENGTH_LONG).show()
            return
        }
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val outputFile = File(outputDir, "FrameStudio_${System.currentTimeMillis()}.mp4")
        exportButton.isEnabled = false
        status.text = "Menyiapkan ekspor..."
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                publishToGallery(outputFile)
                exportButton.isEnabled = true
                status.text = "Ekspor selesai. Video tersimpan di Galeri."
            }
            override fun onError(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                exportButton.isEnabled = true
                status.text = "Ekspor gagal: ${exportException.message ?: "format video tidak didukung"}"
            }
        }
        transformer = Transformer.Builder(this).addListener(listener).build()
        transformer?.start(EditedMediaItem.Builder(builder.build()).build(), outputFile.absolutePath)
    }

    private fun publishToGallery(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FrameStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                file.delete()
            }
        } catch (e: Exception) {
            runOnUiThread { status.text = "Ekspor selesai, file ada di penyimpanan aplikasi. ${e.message ?: ""}" }
        }
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
