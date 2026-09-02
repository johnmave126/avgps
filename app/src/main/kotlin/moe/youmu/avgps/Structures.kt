package moe.youmu.avgps

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

object DateAsLongSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}

object InetAddressSerializer : KSerializer<InetAddress> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InetAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InetAddress) =
        encoder.encodeString(value.hostAddress ?: "")

    override fun deserialize(decoder: Decoder): InetAddress =
        InetAddress.getByName(decoder.decodeString())
}

@Serializable
@SerialName("InetSocketAddress")
private class InetSocketAddressSurrogate(
    @Serializable(with = InetAddressSerializer::class)
    val address: InetAddress,
    val port: Int
) {
    init {
        require(port in 0..65535)
    }
}

object InetSocketAddressSerializer : KSerializer<InetSocketAddress> {
    override val descriptor: SerialDescriptor =
        InetSocketAddressSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: InetSocketAddress) {
        val surrogate = InetSocketAddressSurrogate(value.address, value.port)
        encoder.encodeSerializableValue(InetSocketAddressSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): InetSocketAddress {
        val surrogate = decoder.decodeSerializableValue(InetSocketAddressSurrogate.serializer())
        return InetSocketAddress(surrogate.address, surrogate.port)
    }
}

@Serializable
data class Client(
    @Serializable(with = InetSocketAddressSerializer::class)
    val source: InetSocketAddress,
    val efb: String,
    @Serializable(with = DateAsLongSerializer::class)
    var lastDiscover: Date,
    var isEnabled: Boolean
)

@Serializable
data class LocationData(
    val timestamp: Long,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val horizontalAccuracy: Float,
    val verticalAccuracy: Float,
)

@Serializable
data class SatelliteData(
    val type: String,
    val id: Int,
    val azimuth: Float,
    val elevation: Float,
    val cnr: Float,
    val used: Boolean,
)

@Serializable
data class EFBDiscoveryMessage(
    val App: String,
    val GDL90: PortInfo,
) {
    @Serializable
    data class PortInfo(val port: Int) {
        init {
            require(port in 0..65535)
        }
    }
}