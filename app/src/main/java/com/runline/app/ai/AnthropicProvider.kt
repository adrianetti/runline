package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Provider boundary leaves the recorder and training model independent of any vendor SDK. */
interface CoachAiProvider {
    fun message(key: String, model: String, system: String, data: JSONObject, maxTokens: Int, timeoutMs: Int = 45_000): String
    fun checkModel(key: String, model: String)
}

data class AiHttpResponse(val status: Int, val body: String)
fun interface AiTransport { fun request(method: String, path: String, key: String, body: String?, timeoutMs: Int): AiHttpResponse }
class AiFailure(val reason: Reason) : Exception(reason.name) {
    enum class Reason { KEY, MODEL, RATE_LIMIT, NETWORK, RESPONSE, DISABLED, STORAGE }
}

class AnthropicProvider(private val transport: AiTransport = HttpAiTransport()) : CoachAiProvider {
    override fun checkModel(key: String, model: String) {
        require(AiConfig.validModel(model))
        val response = transport.request("GET", "/v1/models/$model", key, null, 15_000)
        checkResponse(response)
        if (runCatching { JSONObject(response.body).getString("id").isNotBlank() }.getOrDefault(false).not()) throw AiFailure(AiFailure.Reason.RESPONSE)
    }
    override fun message(key: String, model: String, system: String, data: JSONObject, maxTokens: Int, timeoutMs: Int): String {
        require(AiConfig.validModel(model)); require(maxTokens in 100..16_000)
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("stream", false)
            .put("system", system).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", data.toString())))
        val response = transport.request("POST", "/v1/messages", key, body.toString(), timeoutMs)
        checkResponse(response)
        return runCatching {
            val json = JSONObject(response.body)
            require(json.optString("stop_reason") != "max_tokens")
            val blocks = json.getJSONArray("content")
            val text = (0 until blocks.length()).map { blocks.getJSONObject(it) }.filter { it.optString("type") == "text" }
                .joinToString("\n") { it.getString("text") }.trim()
            require(text.isNotBlank() && text.length <= 80_000)
            text
        }.getOrElse { throw AiFailure(AiFailure.Reason.RESPONSE) }
    }
    private fun checkResponse(response: AiHttpResponse) {
        if (response.status in 200..299) return
        throw AiFailure(when (response.status) {
            401, 403 -> AiFailure.Reason.KEY
            400, 404 -> AiFailure.Reason.MODEL
            429 -> AiFailure.Reason.RATE_LIMIT
            else -> AiFailure.Reason.NETWORK
        })
    }
}

/** Fixed HTTPS origin, no redirect credentials, bounded response and timeouts; never logs secrets or bodies. */
class HttpAiTransport : AiTransport {
    override fun request(method: String, path: String, key: String, body: String?, timeoutMs: Int): AiHttpResponse {
        require(path.startsWith("/v1/") && !path.contains('?'))
        val connection = URL("https://api.anthropic.com$path").openConnection() as HttpURLConnection
        val deadline = interrupter.schedule({ connection.disconnect() }, timeoutMs.toLong() + 10_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000; connection.readTimeout = timeoutMs
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("anthropic-version", "2026-10-21")
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8); require(bytes.size <= 180_000)
                connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val source = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = source?.use { it.readNBytesCompat(1_000_000) } ?: byteArrayOf()
            return AiHttpResponse(status, bytes.toString(Charsets.UTF_8))
        } catch (e: AiFailure) { throw e }
        catch (_: Exception) { throw AiFailure(AiFailure.Reason.NETWORK) }
        finally { deadline.cancel(false); connection.disconnect() }
    }
    companion object { private val interrupter = java.util.concurrent.Executors.newSingleThreadScheduledExecutor() }
}
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val size = read(buffer); if (size < 0) break; if (out.size() + size > limit) throw AiFailure(AiFailure.Reason.RESPONSE); out.write(buffer, 0, size) }
    return out.toByteArray()
}
