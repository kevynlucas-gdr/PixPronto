package com.pixpronto.pix

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

/** BR Code (EMVCo) builder for reusable static Pix receiver QR Codes. */
data class PixRequest(
    val key: String,
    val beneficiary: String,
    val city: String,
    val amount: BigDecimal? = null,
    val description: String? = null,
)

object PixPayload {
    private const val GUI = "br.gov.bcb.pix"

    fun build(request: PixRequest): String {
        val key = request.key.trim()
        require(key.isNotEmpty()) { "Informe uma chave Pix." }
        val name = emvText(request.beneficiary, 25)
        val city = emvText(request.city, 15)
        require(name.isNotBlank()) { "Informe o nome do beneficiário." }
        require(city.isNotBlank()) { "Informe a cidade do beneficiário." }

        val merchantAccount = tlv("00", GUI) + tlv("01", key) +
            request.description?.trim()?.takeIf { it.isNotEmpty() }?.let { tlv("02", emvText(it, 72)) }.orEmpty()
        require(merchantAccount.length <= 99) { "Chave e descrição excedem o limite do BR Code." }

        val amount = request.amount?.let {
            require(it > BigDecimal.ZERO) { "O valor deve ser maior que zero." }
            require(it.scale() <= 2 || it.stripTrailingZeros().scale() <= 2) { "Use no máximo duas casas decimais." }
            val normalized = it.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
            require(normalized.length <= 13) { "Valor acima do limite permitido." }
            tlv("54", normalized)
        }.orEmpty()

        // Point of Initiation Method 11 denotes a static, reusable QR Code.
        val body = tlv("00", "01") + tlv("01", "11") + tlv("26", merchantAccount) +
            tlv("52", "0000") + tlv("53", "986") + amount + tlv("58", "BR") +
            tlv("59", name) + tlv("60", city) + tlv("62", tlv("05", "***"))
        val crcInput = body + "6304"
        return crcInput + crc16(crcInput)
    }

    fun validate(payload: String): Boolean {
        if (payload.length < 12 || !Regex("6304[0-9A-F]{4}$").containsMatchIn(payload)) return false
        val crcStart = payload.length - 4
        return crc16(payload.substring(0, crcStart)) == payload.substring(crcStart)
    }

    fun crc16(data: String): String {
        var crc = 0xFFFF
        data.toByteArray(Charsets.UTF_8).forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return crc.toString(16).uppercase(Locale.ROOT).padStart(4, '0')
    }

    private fun tlv(id: String, value: String): String {
        val byteLength = value.toByteArray(Charsets.UTF_8).size
        require(byteLength <= 99) { "Campo $id excede 99 bytes." }
        return id + byteLength.toString().padStart(2, '0') + value
    }

    private fun emvText(value: String, maxLength: Int): String {
        val ascii = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9 $*+\\-./:]"), " ")
            .replace(Regex("\\s+"), " ")
        return ascii.take(maxLength)
    }
}
