package demo 

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class TestSource {
    /*
    @Test fun check_time() {
        val stream_app = StreamsProcessor("kafka:9092")
        val ev: Event = stream_app.jsonMapper.readValue("{\"timestamp\": \"2021-05-01 15:42:12 UTC\", \"views\": 7523}", Event::class.java)
        print(ev.timestamp)
        print(ev.views)
    }
    */
    
    @Test fun check_dates() {
        val text: String = "2011-10-02T14:45:30Z"
        val zone: ZonedDateTime = ZonedDateTime.parse(text)
        val t1: String = "2011-10-03T14:45:30Z"
        val z1: ZonedDateTime = ZonedDateTime.parse(t1)
        val dateFormatter:DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")
        val z2 = ZonedDateTime.parse("2021-05-01 00:01:30 UTC",dateFormatter).withSecond(0)
        print(z2)
        print(z1)
        assertTrue(z1.isAfter(zone))
    }
        
    //TODO: add Javalin Testing https://javalin.io/tutorials/testing
}
