package app.paralleljudge.data

import app.paralleljudge.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class OpenAiClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val spec: ModelSpec
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<String> = callbackFlow {
        val bodyJson = buildJsonObject {
            put("model", spec.modelName)
            put("stream", true)
            request.temperature?.let { put("temperature", it) }
            if (spec.useMaxCompletionTokens) put("max_completion_tokens", request.maxTokens)
            else put("max_tokens", request.maxTokens)
            
            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role.wire)
                        put("content", msg.content)
                    }
                }
            }
        }

        val httpRequest = Request.Builder()
            .url(spec.baseUrl)
            .header("Authorization", "Bearer ${spec.apiKey}")
            .header("Accept", "text/event-stream")
            .post(RequestBody.create("application/json".toMediaType(), bodyJson.toString()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(data).jsonObject
                    val delta = jsonElement["choices"]?.jsonArray?.get(0)?.jsonObject?.get("delta")?.jsonObject?.get("content")
                    delta?.jsonPrimitive?.contentOrNull?.let { trySend(it) }
                } catch (e: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(LlmException("Network Error", LlmException.Kind.NETWORK, cause = t))
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }
}
