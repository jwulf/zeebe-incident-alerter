package io.zeebe;

import io.zeebe.exporter.api.Exporter
import io.zeebe.exporter.api.context.Context
import io.zeebe.exporter.api.context.Controller
import io.zeebe.protocol.record.Record
import org.slf4j.Logger

@Suppress("unused")
class IncidentAlerter: Exporter
{
    private lateinit var log: Logger
    private lateinit var controller: Controller

    override fun configure(context: Context) {
        val filter = RecordFilter()
        context.setFilter(filter)
        this.log = context.logger
    }

    override fun open(controller: Controller) {
        this.controller = controller
        log.info("Incident Alerter Exporter loaded")

    }

    override fun close() {}

    override fun export(record: Record<*>) {
        log.info(record.toString())
        this.controller.updateLastExportedRecordPosition(record.position);
    }
}
