package demo 

import kotlinx.serialization.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.errors.SerializationException;
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.time.ZonedDateTime

/**
 * Event Class
 *
 * This class is used to store the information coming from the Kafka Topic 'view_stream'.
 *
 * @property timestamp when the even was created
 * @property views amount of views in the timestamp instant
 * @property channel which channel are we considering
 */
//@Serializable (with = EvenAsStringSerializer::class)
data class Event(
    //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss z")
    val timestamp: String,

    val views: Int,

    val channel: Int
)


/**
 * CountAndSum Class
 *
 * This class is used to store the aggregate information.
 *
 * @property count how many datapoint we are aggregating
 * @property sum store the sum of the value you want to compute the mean of
 * @property time timestamp that related to the values
 * @property channel which channel are we considering
 */
data class CountAndSum(
    var count: Long,

    var sum: Long,

    var time: String,

    var channel: Int
)
/**
 * CountSumAsStringSerializer Class
 *
 * This class is used to serialize the CountAndSum Objects
 * https://sparkbyexamples.com/kafka/kafka-example-with-custom-serializer/
 */
class CountSumAsStringSerializer: Serializer<CountAndSum> {

    override fun serialize(topic: String, data: CountAndSum): ByteArray {
        val string = "{ \"count\": "+ data.count +", \"sum\": "+ data.sum +", \"time\": \""+ data.time +"\", \"channel\": "+ data.channel +"}"
        return string.toByteArray(Charsets.UTF_8)
    }

    override fun configure(map: Map<String,*>, b: Boolean): Unit {}
    override fun close(): Unit {}
}
/**
 * CountSumAsStringSerializer Class
 *
 * This class is used to deserialize the CountAndSum Objects
 * https://sparkbyexamples.com/kafka/kafka-example-with-custom-serializer/
 */
class CountSumAsStringDeserializer: Deserializer<CountAndSum> {

    val jsonMapper = ObjectMapper().apply{
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    
    override fun deserialize(arg0:String, arg1: ByteArray): CountAndSum {
        return jsonMapper.readValue(arg1, CountAndSum::class.java)
    }

    override fun configure(arg0: Map<String, *>, arg1: Boolean) {}
    override fun close() {}
}