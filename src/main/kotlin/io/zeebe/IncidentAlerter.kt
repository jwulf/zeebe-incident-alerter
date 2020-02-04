package io.zeebe;

import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.zeebe.exporter.api.Exporter
import io.zeebe.exporter.api.context.Context
import io.zeebe.exporter.api.context.Controller
import io.zeebe.protocol.record.Record
import io.zeebe.protocol.record.intent.IncidentIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.slf4j.Logger
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Suppress("unused")
class IncidentAlerter: Exporter
{
    private lateinit var token: String
    private lateinit var url: String
    private lateinit var configuration: IncidentAlerterConfiguration
    private lateinit var client: OkHttpClient
    private lateinit var log: Logger
    private lateinit var controller: Controller

    override fun configure(context: Context) {
        val filter = RecordFilter()
        context.setFilter(filter)
        this.log = context.logger

        configuration = context
                .configuration
                .instantiate(IncidentAlerterConfiguration::class.java)
        url = configuration.url
        token = configuration.token
        if (url == "") {
            log.warn("Incident Alerter: No URL specified for endpoint.")
        } else {
            log.info("Incident Alerter endpoint configured to: $url")
        }
        client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun open(controller: Controller) {
        this.controller = controller
        log.info("Incident Alerter Exporter loaded")

    }

    override fun close() {}

    override fun export(record: Record<*>) {
        try {
            if (record.intent == IncidentIntent.CREATED) {
                log.info(record.toString())
                postIncident(record)
            }
        } catch (e: Throwable) {
                log.error("Error thrown by Incident Exporter!")
                e.printStackTrace()
        } finally {
            this.controller.updateLastExportedRecordPosition(record.position)
        }
    }

    private fun postIncident(record: Record<*>) = runBlocking {
        retry(limitAttempts(20) + binaryExponentialBackoff(base = 10L, max = 5000L)) {
            doPost(record)
        }
    }

    private fun doPost(record: Record<*>) {
        val jSON = "application/json; charset=utf-8".toMediaType()
        val body = record.toJson().toString().toRequestBody(jSON)
        val request = Request.Builder()
                .addHeader("Authorization", "Bearer $token")
                .url(url)
                .post(body)
                .build()
        val c = client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                log.error("Incident Alerter - sending alert to $url failed!")
                e.printStackTrace()
                throw(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        log.error("Incident Alerter - response from $url ${response.code}: ${response.message}")
                    }

                    for ((name, value) in response.headers) {
                        println("$name: $value")
                    }

                    println(response.body!!.string())
                }
            }
        })
    }
}
