package app.paralleljudge.di

import app.paralleljudge.core.ClientResolver
import app.paralleljudge.core.ParallelOrchestrator
import app.paralleljudge.data.LiteRtClient
import app.paralleljudge.data.OpenAiClient
import app.paralleljudge.core.Dialect
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideJson() = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideOkHttp() = OkHttpClient.Builder().build()

    @Provides @Singleton
    fun provideOrchestrator(okHttp: OkHttpClient, json: Json): ParallelOrchestrator {
        val resolver = ClientResolver { spec ->
            when (spec.dialect) {
                Dialect.OPENAI_COMPAT -> OpenAiClient(okHttp, json, spec)
                Dialect.ANTHROPIC -> OpenAiClient(okHttp, json, spec)
                Dialect.LOCAL_LITERT -> LiteRtClient()
            }
        }
        return ParallelOrchestrator(resolver)
    }
}
