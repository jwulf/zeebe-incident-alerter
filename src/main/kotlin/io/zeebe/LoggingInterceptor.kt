package io.zeebe

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import java.io.IOException

internal class LoggingInterceptor(private var log: Logger) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        log.info("%%%%%%%% Interceptor")
        val response: Response = chain.proceed(request)
        log.info("@#$#@$@#$@#$@#$@#$@ Response successful: ${response.networkResponse?.isSuccessful.toString()}")
        // otherwise just pass the original response on
        return response
    }
}