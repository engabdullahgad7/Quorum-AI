package app.paralleljudge.data

import app.paralleljudge.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LiteRtClient : LlmClient {
    override fun stream(request: LlmRequest): Flow<String> = flow {
        emit("مرحباً، ")
        emit("أنا نموذج ")
        emit("محلي.")
    }.flowOn(Dispatchers.Default)
}
