package app.paralleljudge.core

import kotlinx.coroutines.flow.Flow

enum class Dialect { OPENAI_COMPAT, ANTHROPIC, LOCAL_LITERT }

data class ModelSpec(
    val id: String,
    val displayName: String,
    val dialect: Dialect,
    val baseUrl: String = "",
    val modelName: String,
    val apiKey: String? = null,
    val maxTokens: Int = 1024,
    val temperature: Double? = 0.7,
    val timeoutMs: Long = if (dialect == Dialect.LOCAL_LITERT) 240_000L else 90_000L,
    val useMaxCompletionTokens: Boolean = false
) {
    val isLocal: Boolean get() = dialect == Dialect.LOCAL_LITERT
}

enum class Role(val wire: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant") }
data class ChatMessage(val role: Role, val content: String)

data class LlmRequest(
    val messages: List<ChatMessage>,
    val maxTokens: Int,
    val temperature: Double?
)

fun interface LlmClient {
    fun stream(request: LlmRequest): Flow<String>
}

fun interface ClientResolver {
    fun clientFor(spec: ModelSpec): LlmClient
}

class LlmException(
    message: String,
    val kind: Kind,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Kind { AUTH, RATE_LIMIT, PAYMENT, SERVER, BAD_REQUEST, NETWORK, EMPTY, UNKNOWN }
    val isTransient: Boolean get() = kind == Kind.RATE_LIMIT || kind == Kind.SERVER || kind == Kind.NETWORK
}
