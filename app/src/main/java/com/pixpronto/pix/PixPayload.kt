package com.pixpronto.pix

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

enum class PixKeyType(val label: String) {
    CPF("CPF"), CNPJ("CNPJ"), EMAIL("E-mail"), PHONE("Telefone"), RANDOM("Aleatória");

    companion object {
        fun detect(value: String): PixKeyType? {
            val v = value.trim()
            return when {
                v.contains("@") -> EMAIL
                v.filter(Char::isDigit).length == 11 -> CPF
                v.filter(Char::isDigit).length == 14 -> CNPJ
                v.matches(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) -> RANDOM
                v.filter(Char::isDigit).length in listOf(10, 12, 13) -> PHONE
                else -> null
            }
        }
    }
}

object PixKeyFormatter {
    fun normalize(value: String, type: PixKeyType): String {
        val raw = value.trim()
        return when (type) {
            PixKeyType.CPF, PixKeyType.CNPJ -> raw.filter(Char::isDigit)
            PixKeyType.PHONE -> {
                val digits = raw.filter(Char::isDigit)
                when {
                    digits.startsWith("55") && digits.length in 12..13 -> "+$digits"
                    digits.length in 10..11 -> "+55$digits"
                    else -> "+$digits"
                }
            }
            PixKeyType.EMAIL -> raw.lowercase(Locale.ROOT)
            PixKeyType.RANDOM -> raw.lowercase(Locale.ROOT)
        }
    }

    fun format(value: String, type: PixKeyType): String {
        val d = value.filter(Char::isDigit)
        return when (type) {
            PixKeyType.CPF -> if (d.length == 11) "${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}-${d.substring(9)}" else value
            PixKeyType.CNPJ -> if (d.length == 14) "${d.substring(0,2)}.${d.substring(2,5)}.${d.substring(5,8)}/${d.substring(8,12)}-${d.substring(12)}" else value
            PixKeyType.PHONE -> {
                val n = if (d.startsWith("55")) d.drop(2) else d
                when (n.length) {
                    10 -> "+55 (${n.substring(0,2)}) ${n.substring(2,6)}-${n.substring(6)}"
                    11 -> "+55 (${n.substring(0,2)}) ${n.substring(2,7)}-${n.substring(7)}"
                    else -> value
                }
            }
            else -> value
        }
    }

    fun isValid(value: String, type: PixKeyType): Boolean {
        val v = normalize(value, type)
        return when (type) {
            PixKeyType.CPF -> validCpf(v)
            PixKeyType.CNPJ -> validCnpj(v)
            PixKeyType.PHONE -> v.matches(Regex("^\\+55[1-9][0-9]9?[0-9]{8}$"))
            PixKeyType.EMAIL -> v.length <= 77 && Regex("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$", RegexOption.IGNORE_CASE).matches(v)
            PixKeyType.RANDOM -> v.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))
        }
    }

    private fun validCpf(s: String): Boolean {
        if (s.length != 11 || s.toSet().size == 1) return false
        fun digit(length: Int): Int {
            val sum = (0 until length).sumOf { (s[it] - '0') * (length + 1 - it) }
            val r = (sum * 10) % 11
            return if (r == 10) 0 else r
        }
        return digit(9) == s[9] - '0' && digit(10) == s[10] - '0'
    }

    private fun validCnpj(s: String): Boolean {
        if (s.length != 14 || s.toSet().size == 1) return false
        fun check(length: Int): Int {
            val weights = if (length == 12) intArrayOf(5,4,3,2,9,8,7,6,5,4,3,2) else intArrayOf(6,5,4,3,2,9,8,7,6,5,4,3,2)
            val sum = (0 until length).sumOf { (s[it] - '0') * weights[it] }
            val r = sum % 11
            return if (r < 2) 0 else 11-r
        }
        return check(12) == s[12]-'0' && check(13) == s[13]-'0'
    }
}

data class PixRequest(
    val key: String,
    val beneficiary: String,
    val city: String,
    val amount: BigDecimal? = null,
    val description: String? = null,
    val keyType: PixKeyType? = null,
)

object PixPayload {
    private const val GUI = "br.gov.bcb.pix"

    fun build(request: PixRequest): String {
        val type = request.keyType ?: PixKeyType.detect(request.key)
            ?: throw IllegalArgumentException("Selecione o tipo da chave Pix.")
        val key = PixKeyFormatter.normalize(request.key, type)
        require(PixKeyFormatter.isValid(key, type)) { "Chave ${type.label} inválida. Confira os caracteres e os dígitos." }
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

    private fun emvText(value: String, maxLength: Int): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9 $*+\\-./:]"), " ")
            .replace(Regex("\\s+"), " ").take(maxLength)
}
