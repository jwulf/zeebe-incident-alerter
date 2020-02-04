package io.zeebe;

import io.zeebe.exporter.api.Exporter
import io.zeebe.exporter.api.context.Context
import io.zeebe.exporter.api.context.Controller
import io.zeebe.protocol.record.Record
import io.zeebe.protocol.record.intent.IncidentIntent
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

    private fun postIncident(record: Record<*>) {
        val jSON = "application/json; charset=utf-8".toMediaType()
        val body = record.toJson().toString().toRequestBody(jSON)
        val rb = Request.Builder()
        if (token !== "") rb.addHeader("Authorization", "Bearer $token")
        val request = rb.url(url)
                .post(body)
                .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                log.error("Incident Alerter - sending alert to $url failed!")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        log.error("Incident Alerter - response from $url ${response.code}: ${response.message}")
                    }

                    for ((name, value) in response.headers) {
                        log.debug("$name: $value")
                    }

                    log.debug(response.body!!.string())
                }
            }
        })
    }
}
