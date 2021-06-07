package demo


import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.ServiceUnavailableResponse

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.state.ReadOnlyWindowStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.WindowStoreIterator
import org.apache.kafka.streams.state.WindowStore
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.kstream.Materialized

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.log4j.LogManager
import java.time.Duration
import java.time.ZonedDateTime
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.*

import demo.Event
import demo.CountAndSum


/**
 * Main Method
 *
 * This method handles the API endpoints.
 *
 * @property args command line arguments
 */
fun main(args: Array<String>) {

    val stream_app = StreamsProcessor("kafka:9092")
    val app = Javalin.create().start(8080)
    val manager = APIManager(stream_app)


    val jsonMapper = ObjectMapper().apply{
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    app.before("/api/v1/channel/:channel_id/*") { ctx -> 
        val channel = ctx.pathParam("channel_id").toIntOrNull()
        val fromTimeString = ctx.queryParam("from", null)
        
        //Checking the user input a valid channel
        if ( channel == null || channel < 0) {
            throw BadRequestResponse("Not a valid channel ID")
        }
        
        //Checking if from is set
        if ( fromTimeString == null ) {
            throw ForbiddenResponse("Query parameter 'from' must be set")
        }

    }

    app.get("/api/v1/channel/:channel_id/seconds") { ctx ->

        val channel = ctx.pathParam("channel_id").toIntOrNull()
        val fromTime: ZonedDateTime = manager.getTime(ctx.queryParam("from", null)) //(in processing-time)
        var toTime: ZonedDateTime = manager.getTime(ctx.queryParam("to", null)) //(in processing-time)

        if (toTime.getYear() == 0 || fromTime.plusHours(24).isBefore(toTime) || toTime.isBefore(fromTime) ) {
            toTime = fromTime.plusHours(24)
        }

        val allElements = manager.fetchRawData(Instant.ofEpochMilli(fromTime.toEpochSecond()*1000), Instant.ofEpochMilli(toTime.toEpochSecond()*1000))
        
        val events = allElements
                .map { it ->  jsonMapper.readValue(it, Event::class.java) } //Convert JSON string to Event object
                .filter { it ->  it.channel == channel} //Considers only event from the channel selected by the user
                //.map { it -> "{ \"timestamp\": \"" + it.timestamp.toString() + "\", \"views\": " + it.views + "}" }
        
        ctx.json(events)
    }


    app.get("/api/v1/channel/:channel_id/minutes") { ctx -> 
        val channel = ctx.pathParam("channel_id").toIntOrNull()
        val fromTime: ZonedDateTime = manager.getTime(ctx.queryParam("from", null)) //(in processing-time)
        var toTime: ZonedDateTime = manager.getTime(ctx.queryParam("to", null)) //(in processing-time)

        if (toTime.getYear() == 0 || fromTime.plusHours(24).isBefore(toTime) || toTime.isBefore(fromTime) ) {
            toTime = fromTime.plusHours(24)
        }

        val allElements = manager.fetchAggregateData(Instant.ofEpochMilli(fromTime.toEpochSecond()*1000), Instant.ofEpochMilli(toTime.toEpochSecond()*1000))
        
        val events = allElements
                .map { it ->  jsonMapper.readValue(it, Event::class.java) } //Convert JSON string to Event object
                .filter { it ->  it.channel == channel} //Considers only event from the channel selected by the user
                //.map { it -> "{ \"timestamp\": \"" + it.timestamp.toString() + "\", \"views\": " + it.views + "}" }
        
        ctx.json(events)
    }
}
 

/**
 * API Manager Class
 *
 * This class encapsulate all the common methods shared between the two endpoints for easier testing.
 *
 * @property logger used for logging events
 */
class APIManager(sourcestream: StreamsProcessor) {

    private val logger = LogManager.getLogger(javaClass)
    val view_second: ReadOnlyWindowStore<String, String> = sourcestream.process_second()
    val view_minute: ReadOnlyWindowStore<String, String> = sourcestream.process_minute()

    fun getTime(timestring: String?): ZonedDateTime {
        var time: ZonedDateTime = ZonedDateTime.now().withYear(0)
        if (timestring != null) {
            try {
                time = ZonedDateTime.parse(timestring)
                logger.info("Parsed $timestring")
            } catch (e: DateTimeParseException) {
                logger.info("BAD Request arrived. Date is not well formatted")
                throw BadRequestResponse("Query parameter 'from' it is not ISO 8601 ")
            }
        }
        return time
    }

    /**
     * Gets data from Ktable with second granularity.
     * @return a list of JSON strings representing Events.
     */
    fun fetchRawData(fromTime: Instant, toTime: Instant ): MutableList<String> {
        logger.info("Requested from " + fromTime.getEpochSecond() + " to " + toTime.getEpochSecond()) //Debug Purpose
        return fetchData(view_second,fromTime,toTime)
    }

    /**
     * Gets data from Ktable with minutes granularity.
     * @return a list of JSON strings representing Events.
     */
    fun fetchAggregateData(fromTime: Instant, toTime: Instant ): MutableList<String>  {
        logger.info("Requested from " + fromTime.getEpochSecond() + " to " + toTime.getEpochSecond()) //Debug Purpose
        return fetchData(view_minute,fromTime,toTime)
    }

    /**
     * Gets data from given Ktable, between fromTime and ToTime.
     * @return a list of JSON strings representing Events.
     */
    private fun fetchData(table: ReadOnlyWindowStore<String, String>, fromTime: Instant, toTime: Instant ): MutableList<String>  {
        val event_strings: MutableList<String> = arrayListOf()
        var iterator = table.fetchAll(fromTime,toTime)
        while (iterator.hasNext()) {
            var next: KeyValue<Windowed<String>, String> = iterator.next()
            logger.info("Views for channel" + next.key.key() + ", between " + fromTime.getEpochSecond() + " & " + toTime.getEpochSecond() + ", are: " + next.value) //Debug Purpose
            event_strings.add(next.value)
        }
        iterator.close() // close the iterator to release resources
        return event_strings
    }

}


/**
 * StreamProcessor Class
 *
 * This class build and maintain the streaming pipelines for second and minute event granularity.
 *
 * @property logger used for logging events
 */
class StreamsProcessor(val brokers: String) {

    private val logger = LogManager.getLogger(javaClass)

    val jsonMapper = ObjectMapper().apply{
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    /**
     * Creates a streaming pipeline for events at a second granularity.
     * @return a windowed view of the final KTable<Channel_id, Event's JSON>.
     */
    fun process_second(): ReadOnlyWindowStore<String, String> {

        val streamsBuilder = StreamsBuilder()
        val reducer = { v1:String, v2:String -> v1 } //Drop duplicates for the same show in the same second

        val eventKTable: KTable<Windowed<String>, String> = streamsBuilder
                .stream("views_flow", Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey() //Group by channel id
                .windowedBy( TimeWindows.of(Duration.ofSeconds(1)) )
                .reduce(reducer,Materialized.`as`<String, String, WindowStore<org.apache.kafka.common.utils.Bytes, ByteArray>>("WindowStore"))

        val topology = streamsBuilder.build()


        val props = Properties()
        props["bootstrap.servers"] = brokers
        props["application.id"] = "kafka-bigstream"
        val stream_ktable: KafkaStreams = KafkaStreams(topology, props)
        stream_ktable.start()

        return stream_ktable.store("WindowStore", QueryableStoreTypes.windowStore())

    }

    /**
     * Creates a streaming pipeline for events at a minute granularity.
     * @return a windowed view of the final KTable<Channel_id, Event's JSON>.
     */
    fun process_minute(): ReadOnlyWindowStore<String, String> {
        val avgSer: CountSumAsStringSerializer = CountSumAsStringSerializer() 
        val avgDes: CountSumAsStringDeserializer = CountSumAsStringDeserializer()
        val reducer = { v1:String, v2:String -> v1 } //Drop duplicates for the same show in the same second
        val dateFormatter:DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")

        val streamsBuilder = StreamsBuilder()

        val eventJsonStream: KStream<String, String> = streamsBuilder
                .stream("views_flow", Consumed.with(Serdes.String(), Serdes.String()))

        val eventStream: KStream<String, Event> = eventJsonStream.mapValues { v ->
            val view_ev = jsonMapper.readValue(v, Event::class.java)
            //logger.info("Event: $view_ev")
            view_ev
        }

        val viewsRawAverage: KStream<Windowed<String>, CountAndSum> = eventStream.groupByKey().windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                //Compute the views average across 1 min window
                .aggregate(
                    { CountAndSum(0L, 0L, "", 0) }, //Initalize counter
                    { key:String, event:Event, aggregate:CountAndSum ->
                        aggregate.count = aggregate.count +1
                        aggregate.sum = aggregate.sum + event.views
                        aggregate.time = ZonedDateTime.parse(event.timestamp,dateFormatter).withSecond(0).toString()
                        aggregate.channel = event.channel
                        aggregate
                    }, Materialized.with(Serdes.String(),Serdes.serdeFrom(avgSer,avgDes)) 
                )
                //Back to Stream window is dropped
                .toStream() 

        val viewsAverage:  KTable<Windowed<String>, String> = viewsRawAverage
                .mapValues{ infoavg: CountAndSum -> 
                    val ev: Event = Event(infoavg.time, (infoavg.sum / infoavg.count).toInt(), infoavg.channel) 
                    val evStr: String = jsonMapper.writeValueAsString(ev)
                    //logger.info("Event: $evStr")
                    evStr
                }
                //Reset the key removing the window
                .selectKey{ key:Windowed<String>, _ -> /*logger.info("Event: $key");*/ key.key() }
                //Redo windowing steps
                .groupByKey(Grouped.with(Serdes.String(),Serdes.String()))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                //Drop duplicates
                .reduce(reducer, Materialized.`as`<String, String, WindowStore<org.apache.kafka.common.utils.Bytes, ByteArray>>("MinuteWindowStore"))
                
        val topology = streamsBuilder.build()


        val props = Properties()
        props["bootstrap.servers"] = brokers
        props["application.id"] = "kafka-bigstream-minutes"
        val stream_ktable: KafkaStreams = KafkaStreams(topology, props)
        stream_ktable.start()

        return stream_ktable.store("MinuteWindowStore", QueryableStoreTypes.windowStore())
    }
     
}