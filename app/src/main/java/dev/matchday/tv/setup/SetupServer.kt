package dev.matchday.tv.setup

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

data class SetupInfo(
    val url: String,
    val code: String,
    val port: Int,
)

data class SetupPayload(
    val name: String,
    val playlistUrl: String,
    val epgUrl: String?,
    /** Tick on the phone's setup page: also index movies & series. Default on. */
    val includeVod: Boolean = true,
)

/**
 * Tiny LAN-only setup page. The TV runs this while Settings is open; a phone on the same Wi-Fi
 * opens the URL, enters the pairing code shown on TV, and sends a playlist to the device.
 *
 * No cloud relay, no account and no traffic leaves the local network.
 */
class SetupServer(
    private val port: Int = DEFAULT_PORT,
) {
    @Volatile
    private var server: ServerSocket? = null
    @Volatile
    private var worker: Thread? = null
    private var onImport: ((SetupPayload) -> Unit)? = null

    fun start(onImport: (SetupPayload) -> Unit): SetupInfo? {
        stop()
        this.onImport = onImport
        val socket = runCatching { ServerSocket(port) }.getOrElse {
            runCatching { ServerSocket(0) }.getOrNull() ?: return null
        }
        server = socket
        val code = (1000..9999).random().toString()
        val ip = localIpv4() ?: "127.0.0.1"
        val info = SetupInfo(
            url = "http://$ip:${socket.localPort}",
            code = code,
            port = socket.localPort,
        )
        worker = thread(name = "matchday-setup", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { handle(client, info) }
            }
        }
        return info
    }

    fun stop() {
        onImport = null
        runCatching { server?.close() }
        server = null
        worker = null
    }

    val running: Boolean get() = server?.isClosed == false

    private fun handle(client: Socket, info: SetupInfo) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            val headers = readHeaders(input)
            val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 64 * 1024) ?: 0
            val body = readBody(input, length)
            val path = target.substringBefore('?')
            val query = parseForm(target.substringAfter('?', ""))

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    respond(socket, 200, page(info, query["code"] ?: info.code))
                }
                method == "POST" && path == "/import" -> {
                    val form = query + parseForm(body)
                    if (form["code"] != info.code) {
                        respond(socket, 403, resultPage(false, "Pairing code did not match."))
                        return
                    }
                    val url = form["url"]?.trim().orEmpty()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        respond(socket, 400, resultPage(false, "Enter an http(s) playlist URL."))
                        return
                    }
                    val payload = SetupPayload(
                        name = form["name"]?.trim()?.takeIf { it.isNotBlank() } ?: "Phone playlist",
                        playlistUrl = url,
                        epgUrl = form["epg"]?.trim()?.takeIf { it.isNotBlank() },
                        includeVod = form["vod"] != null,
                    )
                    onImport?.invoke(payload)
                    respond(
                        socket,
                        200,
                        resultPage(
                            true,
                            "Sent to the TV. Keep the TV on this screen until the import finishes.",
                        ),
                    )
                }
                else -> respond(socket, 404, resultPage(false, "Not found."))
            }
        }
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val index = line.indexOf(':')
            if (index <= 0) continue
            headers[line.substring(0, index).trim().lowercase()] =
                line.substring(index + 1).trim()
        }
        return headers
    }

    private fun readBody(input: BufferedInputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n <= 0) break
            read += n
        }
        return String(bytes, 0, read, StandardCharsets.UTF_8)
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return if (out.isEmpty()) null else out.toString()
            if (value == '\n'.code) return out.toString().trimEnd('\r')
            out.append(value.toChar())
            if (out.length > 8192) return null
        }
    }

    private fun parseForm(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index < 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, index), "UTF-8")
            val value = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun respond(socket: Socket, status: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        socket.getOutputStream().apply {
            write(headers)
            write(bytes)
            flush()
        }
    }

    private fun page(info: SetupInfo, code: String): String = """
        <!doctype html>
        <html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Matchday setup</title>
        <style>
          :root{color-scheme:dark}body{margin:0;background:#151513;color:#f4efe4;font:16px system-ui,sans-serif}
          main{max-width:560px;margin:0 auto;padding:28px 20px 48px}
          h1{font-size:32px;margin:0 0 4px}p{color:#aaa49a;line-height:1.45}
          label{display:block;margin:18px 0 6px;font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#cf8b63}
          label.check{display:flex;gap:10px;align-items:center;font-size:14px;letter-spacing:0;text-transform:none;color:#f4efe4;margin:22px 0 0;cursor:pointer}
          label.check input{width:auto}
          input{width:100%;box-sizing:border-box;background:#201f1c;border:1px solid #3d3a34;color:#f4efe4;border-radius:8px;padding:13px;font-size:16px}
          button{width:100%;margin-top:24px;background:#d4643c;color:#fff;border:0;border-radius:8px;padding:15px;font-size:17px;font-weight:700}
          .code{background:#201f1c;border:1px solid #3d3a34;border-radius:8px;padding:14px;text-align:center;letter-spacing:.35em;font-size:28px;margin:18px 0}
          small{color:#77726a}
        </style></head>
        <body><main>
          <h1>Matchday</h1>
          <p>Send a playlist to your TV over your home network. Nothing is uploaded to a server.</p>
          <div class="code">$code</div>
          <form method="post" action="/import">
            <input type="hidden" name="code" value="${escapeHtml(code)}">
            <label for="name">Playlist name</label>
            <input id="name" name="name" placeholder="Living room" autocomplete="off">
            <label for="url">Playlist URL</label>
            <input id="url" name="url" type="url" required placeholder="https://provider.example/get.php?..." autocomplete="off">
            <label for="epg">XMLTV EPG URL <small>(optional)</small></label>
            <input id="epg" name="epg" type="url" placeholder="https://provider.example/guide.xml" autocomplete="off">
            <label class="check"><input type="checkbox" name="vod" value="1" checked> Also index movies &amp; series (VOD)</label>
            <button type="submit">Send to TV</button>
          </form>
          <p><small>M3U, M3U8 and Xtream get.php links are accepted. Keep this page open until the TV confirms.</small></p>
        </main></body></html>
    """.trimIndent()

    private fun resultPage(ok: Boolean, message: String): String = """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Matchday setup</title>
        <style>body{margin:0;background:#151513;color:#f4efe4;font:16px system-ui,sans-serif}
        main{max-width:520px;margin:0 auto;padding:48px 20px}h1{font-size:30px}
        p{color:${if (ok) "#8fbd95" else "#d4643c"};line-height:1.5}a{color:#f4efe4}</style></head>
        <body><main><h1>${if (ok) "Sent" else "Not sent"}</h1><p>${escapeHtml(message)}</p>
        <p><a href="/">Back to setup</a></p></main></body></html>
    """.trimIndent()

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull { it != null && !it.startsWith("127.") }
    }.getOrNull()

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
