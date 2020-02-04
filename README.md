# Zeebe Incident Alerter

This Zeebe exporter will call a configured webhook on the creation of an incident. 

See the notes on "Loss of Alerts" and "Mitigation and System Design", before using this in production!

## Building

```
mvn package
```

## Testing with Docker

* Build the package
* Edit `zeebe.cfg.toml` in this directory and add your webhook url - you can get a dynamic one at [webhook.site](https://webhook.site/).
* Run `docker-compose up` in this directory.
* In another terminal, run `zbctl deploy --insecure bpmn/create-incident.bpmn`
* Run `zbctl create instance --insecure create-incident`. This will raise an incident, and the exporter will call your webhook.

## Usage

* Copy the built jar file to `zeebe/lib/`. 
* Add the following to the `zeebe.cfg.toml` file:

```
[[exporters]]
id = "IncidentAlerter"
className = "io.zeebe.IncidentAlerter"

    [exporters.args]
    url="https://your-webhook-endpoint"
    # token="Some optional token for authorization header `Bearer ${token}`
```

The payload posted to your webhook looks like this: 

```
{
  "partitionId": 1,
  "value": {
    "errorMessage": "No data found for query does_not_exist.",
    "bpmnProcessId": "create-incident",
    "workflowKey": 2251799813685249,
    "elementId": "Task_194z010",
    "elementInstanceKey": 2251799813685262,
    "workflowInstanceKey": 2251799813685258,
    "errorType": "IO_MAPPING_ERROR",
    "jobKey": -1,
    "variableScopeKey": 2251799813685262
  },
  "sourceRecordPosition": 4294998144,
  "position": 4294998496,
  "recordType": "EVENT",
  "valueType": "INCIDENT",
  "timestamp": 1580785145828,
  "intent": "CREATED",
  "rejectionType": "NULL_VAL",
  "rejectionReason": "",
  "key": 2251799813685263
}
```

## Loss of Alerts

Making software work is 10% of coding - the other 90% is coding for what happens when it doesn't work.

If your webhook server URL is malformed, or the server is down, or it responds with a status 500 or other error code, this exporter makes no attempt to retry. That incident alert is lost.

The POST operation is asynchronous using the OKHTTP3 library, which uses it own thread pool, and it silently retries on some failures. However, if the webserver is just not there, it will fail.

I made some investigation into retry strategies using Kotlin coroutines or creating a thread pool.

However, at some point you have to choose which is more important: getting the alert or keeping the broker running. You have to either buffer the incidents in memory while retrying, or not advance the exporter position. Not advancing the exporter position will cause the disk usage to grow, as the broker cannot truncate the event log until the exporter has marked the events as exported. If you buffer the incidents in memory, then a broker failure or K8s pod reschedule will lose these incident alerts.

If your incident webhook server is unavailable then either disk or memory usage increases, and probably both, if you want to be _dead sure_ that you get the incident alerts. 

These are complex system design decisions, and generalising them in this exporter is out of scope.

I opted to keep it simple and just give up if the webhook fails. It's a best effort approach, and has minimal impact on the broker.

## Mitigation and System Design

If I were designing incident alerting into a Zeebe system, I would look at a couple of things with this:

1. If I used this exporter, I would deploy a process with a timer start event, and a worker servicing the task in that process that raises an specific incident, then resolves it. In the webhook server, I would test for this specific incident incoming and ping a [healthchecks.io](https://healthchecks.io) endpoint. That healthcheck would warn me if the broker -> exporter -> webhook integration were down.
2. Alternatively, I would build something that reads the ElasticSearch export for incidents. You still need to run known incidents through the system to verify that it is working, so on the whole, this exporter with its one-shot webhook may work for you. One thing that can't be modified with the exporter once the broker is started is the webhook endpoint, but with the ES reader it is a pull model.

