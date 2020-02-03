package io.zeebe

import io.zeebe.exporter.api.context.Context
import io.zeebe.protocol.record.RecordType
import io.zeebe.protocol.record.ValueType

class RecordFilter: Context.RecordFilter {
    override fun acceptType(recordType: RecordType?): Boolean {
        return true
    }

    override fun acceptValue(valueType: ValueType?): Boolean {
        val isIncident = valueType?.equals(ValueType.INCIDENT);
        return isIncident ?: false
    }

}