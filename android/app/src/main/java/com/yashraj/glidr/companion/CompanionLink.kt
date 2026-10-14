package com.yashraj.glidr.companion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.yashraj.glidr.update.Release
import com.yashraj.glidr.update.Versions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** What Windows is playing, as reported by the Glidr companion on the PC. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val app: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val art: ImageBitmap?,
    /** Uptime when [positionMs] was received, so the UI can keep the bar moving between updates. */
    val receivedAtMs: Long,
)

enum class CompanionState { Off, Listening, Connected }

/** Where the PC downloads the companion .exe from. Must match the release asset name; blank turns auto-install off. */
const val COMPANION_DOWNLOAD_URL = "https://github.com/yashraj-shitole/Glidr/releases/latest/download/GlidrCompanion.exe"

/**
 * One-liner typed into the Windows Run box: downloads the companion into %LOCALAPPDATA%\Glidr and starts it.
 * Uses only characters every keyboard layout can type and no quotes, so it survives Run's argument handling.
 */
fun companionInstallCommand(url: String): String {
    val exe = "\$env:LOCALAPPDATA/Glidr/GlidrCompanion.exe"
    return "powershell -w hidden -c md \$env:LOCALAPPDATA/Glidr -f;iwr $url -OutFile $exe;start $exe"
}

/**
 * Receives "now playing" lines from the Glidr companion over a Bluetooth RFCOMM serial link. The phone is the
 * server and the PC connects, so only devices already paired with this phone can reach it.
 *
 * Protocol: one JSON object per line, UTF-8. `{"t":"np", title, artist, album, app, playing, pos, dur, art?}`
 * (pos and dur in milliseconds, art is a small base64 JPEG sent only when the track changes) or `{"t":"none"}`
 * when nothing is playing. `{"t":"release", v, apk, page}` reports the latest Glidr release the companion found on
 * GitHub, so the phone hears about updates even when it cannot check itself.
 */
@SuppressLint("MissingPermission") // guarded by [hasPermission]
class CompanionLink(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(CompanionState.Off)
    val state: StateFlow<CompanionState> = _state.asStateFlow()
    private val _peer = MutableStateFlow<String?>(null)
    val peer: StateFlow<String?> = _peer.asStateFlow()
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    private val _release = MutableStateFlow<Release?>(null)
    /** Latest release as reported by the PC companion (kept after it disconnects). */
    val release: StateFlow<Release?> = _release.asStateFlow()

    private var job: Job? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var client: BluetoothSocket? = null
    private var lastArt: ImageBitmap? = null
    private var lastKey = ""

    private fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (job?.isActive == true || adapter == null || !hasPermission()) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (adapter.isEnabled != true) {
                    _state.value = CompanionState.Off
                    delay(RETRY_MS)
                    continue
                }
                val s = try {
                    adapter.listenUsingRfcommWithServiceRecord("Glidr", SERVICE_UUID)
                } catch (e: IOException) {
                    Log.d(TAG, "companion listen failed: ${e.message}")
                    _state.value = CompanionState.Off
                    delay(RETRY_MS)
                    continue
                } catch (e: SecurityException) {
                    return@launch
                }
                server = s
                _state.value = CompanionState.Listening
                try {
                    while (isActive) handle(s.accept())
                } catch (e: IOException) {
                    Log.d(TAG, "companion accept ended: ${e.message}")
                } finally {
                    runCatching { s.close() }
                    server = null
                    _state.value = CompanionState.Off
                }
                delay(1_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { server?.close() }
        runCatching { client?.close() }
        _state.value = CompanionState.Off
        _nowPlaying.value = null
    }

    private fun handle(socket: BluetoothSocket) {
        client = socket
        _peer.value = runCatching { socket.remoteDevice.name }.getOrNull() ?: socket.remoteDevice.address
        _state.value = CompanionState.Connected
        Log.d(TAG, "companion connected: ${_peer.value}")
        try {
            val input = socket.inputStream
            while (true) {
                val line = readLine(input) ?: break
                parse(line)
            }
        } catch (e: IOException) {
            Log.d(TAG, "companion read ended: ${e.message}")
        } finally {
            runCatching { socket.close() }
            client = null
            _peer.value = null
            _nowPlaying.value = null
            lastArt = null
            lastKey = ""
            _state.value = CompanionState.Listening
        }
    }

    /** Reads up to a newline, refusing absurdly long lines. Returns null at end of stream. */
    private fun readLine(input: InputStream): String? {
        val buf = java.io.ByteArrayOutputStream(512)
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8.name())
            if (buf.size() >= MAX_LINE) throw IOException("line too long")
            buf.write(b)
        }
    }

    private fun parse(line: String) {
        val o = try {
            JSONObject(line)
        } catch (_: Exception) {
            return
        }
        when (o.optString("t")) {
            "release" -> {
                val v = o.optString("v").take(32)
                val page = o.optString("page")
                val apk = o.optString("apk")
                if (v.isNotBlank() && Versions.isTrustedLink(page)) {
                    _release.value = Release(v, apk.takeIf(Versions::isTrustedLink) ?: page, page)
                }
            }
            "none" -> {
                _nowPlaying.value = null
                lastArt = null
                lastKey = ""
            }
            "np" -> {
                val title = o.optString("title").take(300)
                val artist = o.optString("artist").take(300)
                val album = o.optString("album").take(300)
                val key = "$title|$artist|$album"
                val artB64 = o.optString("art")
                if (artB64.isNotEmpty()) {
                    lastArt = decodeArt(artB64)
                } else if (key != lastKey) {
                    lastArt = null
                }
                lastKey = key
                _nowPlaying.value = NowPlaying(
                    title = title,
                    artist = artist,
                    album = album,
                    app = o.optString("app").take(100),
                    playing = o.optBoolean("playing"),
                    positionMs = o.optLong("pos").coerceAtLeast(0),
                    durationMs = o.optLong("dur").coerceAtLeast(0),
                    art = lastArt,
                    receivedAtMs = android.os.SystemClock.elapsedRealtime(),
                )
            }
        }
    }

    private fun decodeArt(b64: String): ImageBitmap? = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth in 1..MAX_ART_PX && bounds.outHeight in 1..MAX_ART_PX) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Must match the companion app on Windows. */
        val SERVICE_UUID: UUID = UUID.fromString("7b2f3c10-6a1e-4c8e-9d52-2f6b1a9c4e01")
        private const val TAG = "Glidr"
        private const val RETRY_MS = 3_000L
        private const val MAX_LINE = 3_000_000
        private const val MAX_ART_PX = 1600
    }
}
