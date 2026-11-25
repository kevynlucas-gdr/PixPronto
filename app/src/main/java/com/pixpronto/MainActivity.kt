
package com.pixpronto

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.room.Room
import com.pixpronto.data.*
import com.pixpronto.pix.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = PixDatabase.get(this)
        setContent { PixProntoApp(db) }
    }
}

private enum class AppTab(val title: String) {
    GENERATE("Criar"), HISTORY("Histórico"), KEYS("Chaves"), SETTINGS("Ajustes")
}

@Composable
private fun PixProntoApp(db: PixDatabase) {
    val context = LocalContext.current
    val dao = db.pixDao()
    val history by dao.history().collectAsState(initial = emptyList())
    val keys by dao.keys().collectAsState(initial = emptyList())
    val prefs = remember { context.getSharedPreferences("pixpronto_settings", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
    var tab by remember { mutableStateOf(AppTab.GENERATE) }

    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9ADDB7),
            secondary = Color(0xFFB9DCC5),
            surface = Color(0xFF171D19)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF087A46),
            onPrimary = Color.White,
            secondary = Color(0xFF426B55),
            background = Color(0xFFF5F8F4),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EFE9)
        )
    }

    MaterialTheme(colorScheme = colors, typography = Typography()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    AppTab.entries.forEach { item ->
                        val icon = when (item) {
                            AppTab.GENERATE -> Icons.Rounded.QrCode2
                            AppTab.HISTORY -> Icons.Rounded.History
                            AppTab.KEYS -> Icons.Rounded.Key
                            AppTab.SETTINGS -> Icons.Rounded.Tune
                        }

                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(icon, null) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                AppTab.GENERATE -> GenerateScreen(Modifier.padding(padding), keys, dao)
                AppTab.HISTORY -> HistoryScreen(Modifier.padding(padding), history, dao)
                AppTab.KEYS -> KeysScreen(Modifier.padding(padding), keys, dao)
                AppTab.SETTINGS -> SettingsScreen(
                    Modifier.padding(padding),
                    theme,
                    {
                        theme = it
                        prefs.edit().putString("theme", it).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateScreen(
    modifier: Modifier,
    keys: List<PixKeyEntity>,
    dao: PixDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyType by remember { mutableStateOf(PixKeyType.CPF) }
    var key by remember { mutableStateOf("") }
    var beneficiary by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("CAMPINA GRANDE") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultMode by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var savedExpanded by remember { mutableStateOf(false) }

    if (resultMode && payload != null && qr != null) {
        ResultScreen(
            modifier,
            qr!!,
            payload!!,
            beneficiary,
            amount,
            keyType,
            onBack = { resultMode = false },
            onCopy = { copyText(context, payload!!) },
            onExport = { format ->
                exportQr(context, qr!!, payload!!, beneficiary, format)
            }
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 22.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "PixPronto",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Seu Pix, simples e do seu jeito.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AccountBalance,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Dados de recebimento",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (keys.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = savedExpanded,
                            onExpandedChange = { savedExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = "Usar uma chave salva",
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(savedExpanded)
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = savedExpanded,
                                onDismissRequest = { savedExpanded = false }
                            ) {
                                keys.forEach { saved ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(saved.label, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    saved.type + " · " + saved.value,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        },
                                        onClick = {
                                            keyType = runCatching {
                                                PixKeyType.valueOf(
                                                    saved.type.uppercase()
                                                        .replace("E-MAIL", "EMAIL")
                                                        .replace("TELEFONE", "PHONE")
                                                        .replace("ALEATÓRIA", "RANDOM")
                                                )
                                            }.getOrDefault(PixKeyType.CPF)

                                            key = saved.value
                                            beneficiary = saved.beneficiary
                                            city = saved.city
                                            savedExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = keyType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de chave Pix") },
                            leadingIcon = { Icon(Icons.Rounded.Category, null) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            PixKeyType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.label) },
                                    onClick = {
                                        keyType = t
                                        key = ""
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = key,
                        onValueChange = { input ->
                            key = when (keyType) {
                                PixKeyType.CPF -> input.filter { it.isDigit() }
                                    .take(11).let { PixKeyFormatter.format(it, keyType) }

                                PixKeyType.CNPJ -> input.filter { it.isDigit() }
                                    .take(14).let { PixKeyFormatter.format(it, keyType) }

                                PixKeyType.PHONE -> input.filter {
                                    it.isDigit() || it == '+' || it == ' ' ||
                                        it == '(' || it == ')' || it == '-'
                                }.take(20)

                                PixKeyType.EMAIL -> input.take(77)
                                PixKeyType.RANDOM -> input.take(36)
                            }
                            payload = null
                        },
                        label = { Text("Chave Pix") },
                        placeholder = { Text(keyHint(keyType)) },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        supportingText = { Text(keyTypeHint(keyType)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (keyType) {
                                PixKeyType.CPF, PixKeyType.CNPJ, PixKeyType.PHONE ->
                                    KeyboardType.Phone
                                PixKeyType.EMAIL -> KeyboardType.Email
                                else -> KeyboardType.Ascii
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = beneficiary,
                        onValueChange = { beneficiary = it; payload = null },
                        label = { Text("Nome do recebedor") },
                        leadingIcon = { Icon(Icons.Rounded.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = city,
                        onValueChange = {
                            city = it.uppercase(Locale("pt", "BR")).take(15)
                            payload = null
                        },
                        label = { Text("Cidade") },
                        leadingIcon = { Icon(Icons.Rounded.LocationCity, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Detalhes do Pix",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it.filter { c ->
                                c.isDigit() || c == ',' || c == '.'
                            }.take(12)
                            payload = null
                        },
                        label = { Text("Valor (opcional)") },
                        leadingIcon = { Icon(Icons.Rounded.AttachMoney, null) },
                        placeholder = { Text("Deixe vazio para valor livre") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(72); payload = null },
                        label = { Text("Descrição (opcional)") },
                        leadingIcon = { Icon(Icons.Rounded.Notes, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        item {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    try {
                        val normalizedKey = PixKeyFormatter.normalize(key, keyType)
                        require(PixKeyFormatter.isValid(normalizedKey, keyType)) {
                            "Chave ${keyType.label} inválida. Confira os caracteres e os dígitos."
                        }

                        val value = amount.trim().takeIf { it.isNotEmpty() }
                            ?.let { BigDecimal(it.replace(',', '.')) }

                        val generated = PixPayload.build(
                            PixRequest(
                                normalizedKey,
                                beneficiary,
                                city,
                                value,
                                description,
                                keyType
                            )
                        )

                        val bitmap = QrCode.bitmap(generated, 1200)
                        qr = bitmap
                        payload = generated
                        error = null
                        resultMode = true

                        scope.launch {
                            dao.insertHistory(
                                PixHistoryEntity(
                                    payload = generated,
                                    beneficiary = beneficiary,
                                    amount = value?.toPlainString(),
                                    description = description.ifBlank { null }
                                )
                            )
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Não foi possível gerar o QR Code."
                        payload = null
                        qr = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Rounded.QrCode2, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Gerar QR Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Text(
                "Seus dados permanecem neste aparelho. O PixPronto não confirma pagamentos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun keyHint(type: PixKeyType) = when (type) {
    PixKeyType.CPF -> "000.000.000-00"
    PixKeyType.CNPJ -> "00.000.000/0000-00"
    PixKeyType.PHONE -> "+55 (00) 00000-0000"
    PixKeyType.EMAIL -> "nome@exemplo.com"
    PixKeyType.RANDOM -> "UUID da chave aleatória"
}

private fun keyTypeHint(type: PixKeyType) = when (type) {
    PixKeyType.CPF -> "11 números; pontos e hífen são aplicados automaticamente."
    PixKeyType.CNPJ -> "14 números; máscara aplicada automaticamente."
    PixKeyType.PHONE -> "DDD + número, com código do Brasil."
    PixKeyType.EMAIL -> "Até 77 caracteres."
    PixKeyType.RANDOM -> "UUID com 36 caracteres."
}

@Composable
private fun ResultScreen(
    modifier: Modifier,
    bitmap: Bitmap,
    payload: String,
    beneficiary: String,
    amount: String,
    type: PixKeyType,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onExport: (String) -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Voltar")
            }
            Text(
                "Seu QR Code Pix",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "QR Code Pix",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    beneficiary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(type.label, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (amount.isNotBlank()) {
                    Text(
                        runCatching {
                            NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                                .format(BigDecimal(amount.replace(',', '.')))
                        }.getOrDefault(amount),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("Valor livre", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                    "QR Code pronto · pagamento ainda não confirmado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("Copiar Pix Copia e Cola")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onExport("png") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Image, null)
                Spacer(Modifier.width(4.dp))
                Text("PNG")
            }

            OutlinedButton(
                onClick = { onExport("pdf") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PictureAsPdf, null)
                Spacer(Modifier.width(4.dp))
                Text("PDF")
            }

            OutlinedButton(
                onClick = { onExport("svg") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Code, null)
                Spacer(Modifier.width(4.dp))
                Text("SVG")
            }
        }
    }
}

private fun copyText(context: Context, value: String) {
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pix Copia e Cola", value))
    Toast.makeText(context, "Pix Copia e Cola copiado", Toast.LENGTH_SHORT).show()
}

private fun exportQr(
    context: Context,
    bitmap: Bitmap,
    payload: String,
    beneficiary: String,
    format: String
) {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "pixpronto-${System.currentTimeMillis()}.$format")

        when (format) {
            "png" -> FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            "svg" -> {
                val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                    payload,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    512,
                    512
                )

                val path = buildString {
                    append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" fill="#fff"/><path fill="#000" d=" """)
                    for (y in 0 until 512) {
                        for (x in 0 until 512) {
                            if (matrix[x, y]) append("M$x,$y h1 v1 h-1z ")
                        }
                    }
                    append(""""/></svg>""")
                }
                file.writeText(path)
            }

            "pdf" -> {
                val doc = PdfDocument()
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, 1).create()
                )
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textAlign = Paint.Align.CENTER
                }

                page.canvas.drawText(
                    "PixPronto", 297f, 90f,
                    paint.apply { textSize = 30f; isFakeBoldText = true }
                )
                page.canvas.drawText(
                    beneficiary.take(30), 297f, 135f,
                    paint.apply { textSize = 18f; isFakeBoldText = false }
                )

                val scaled = Bitmap.createScaledBitmap(bitmap, 420, 420, false)
                page.canvas.drawBitmap(scaled, 87f, 190f, paint)

                page.canvas.drawText(
                    "Pix Copia e Cola", 297f, 660f,
                    paint.apply { textSize = 15f }
                )
                page.canvas.drawText(
                    payload.take(75), 297f, 690f,
                    paint.apply { textSize = 9f }
                )

                doc.finishPage(page)
                FileOutputStream(file).use { doc.writeTo(it) }
                doc.close()
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val mime = when (format) {
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            else -> "image/svg+xml"
        }

        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Compartilhar Pix"
            )
        )
    }.onFailure {
        Toast.makeText(
            context,
            "Falha ao exportar: ${it.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    history: List<PixHistoryEntity>,
    dao: PixDao
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Histórico",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${history.size} códigos neste aparelho",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { if (history.isNotEmpty()) confirmClear = true },
                enabled = history.isNotEmpty()
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    "Limpar histórico",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (history.isEmpty()) {
            EmptyState(
                Icons.Rounded.History,
                "Seu histórico está vazio",
                "Os QR Codes que você gerar aparecerão aqui."
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(22.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.beneficiary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(item.amount?.let { "R$ $it" } ?: "Valor livre")
                            item.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                SimpleDateFormat(
                                    "dd/MM/yyyy · HH:mm",
                                    Locale("pt", "BR")
                                ).format(Date(item.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { copyText(context, item.payload) }) {
                            Icon(Icons.Rounded.ContentCopy, "Copiar")
                        }

                        IconButton(
                            onClick = { scope.launch { dao.deleteHistory(item.id) } }
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                "Excluir",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Limpar histórico?") },
            text = {
                Text(
                    "Todos os ${history.size} registros serão apagados deste aparelho. Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { dao.clearHistory() }
                        confirmClear = false
                    }
                ) {
                    Text("Limpar tudo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeysScreen(
    modifier: Modifier,
    keys: List<PixKeyEntity>,
    dao: PixDao
) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PixKeyType.CPF) }
    var beneficiary by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("CAMPINA GRANDE") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Minhas chaves",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Guarde seus dados para gerar mais rápido.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        label,
                        { label = it },
                        label = { Text("Apelido") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(
                            type.label,
                            {},
                            readOnly = true,
                            label = { Text("Tipo de chave") },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )

                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            PixKeyType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.label) },
                                    onClick = {
                                        type = t
                                        value = ""
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value,
                        { input ->
                            value = when (type) {
                                PixKeyType.CPF -> input.filter { it.isDigit() }
                                    .take(11).let { PixKeyFormatter.format(it, type) }
                                PixKeyType.CNPJ -> input.filter { it.isDigit() }
                                    .take(14).let { PixKeyFormatter.format(it, type) }
                                PixKeyType.PHONE -> input.take(20)
                                PixKeyType.EMAIL -> input.take(77)
                                PixKeyType.RANDOM -> input.take(36)
                            }
                        },
                        label = { Text("Chave Pix") },
                        placeholder = { Text(keyHint(type)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (type == PixKeyType.EMAIL) {
                                KeyboardType.Email
                            } else {
                                KeyboardType.Text
                            }
                        )
                    )

                    OutlinedTextField(
                        beneficiary,
                        { beneficiary = it },
                        label = { Text("Beneficiário") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        city,
                        { city = it.uppercase(Locale("pt", "BR")) },
                        label = { Text("Cidade") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Button(
                        onClick = {
                            val normalized = PixKeyFormatter.normalize(value, type)

                            if (label.isBlank() || beneficiary.isBlank() || city.isBlank()) {
                                error = "Preencha apelido, beneficiário e cidade."
                            } else if (!PixKeyFormatter.isValid(normalized, type)) {
                                error = "Chave ${type.label} inválida."
                            } else {
                                scope.launch {
                                    runCatching {
                                        dao.insertKey(
                                            PixKeyEntity(
                                                label = label.trim(),
                                                type = type.name,
                                                value = normalized,
                                                beneficiary = beneficiary.trim(),
                                                city = city.trim().uppercase(Locale("pt", "BR")),
                                                isDefault = keys.isEmpty()
                                            )
                                        )
                                    }.onFailure {
                                        error = "Não foi possível salvar. Verifique se a chave já existe."
                                    }
                                }

                                label = ""
                                value = ""
                                error = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Salvar chave")
                    }
                }
            }
        }

        items(keys, key = { it.id }) { item ->
            Card(shape = RoundedCornerShape(18.dp)) {
                ListItem(
                    headlineContent = {
                        Text(item.label, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("${item.type} · ${item.value}")
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch { dao.deleteKey(item) }
                        }) {
                            Icon(
                                Icons.Rounded.Delete,
                                "Excluir",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    theme: String,
    onTheme: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Personalize sua experiência.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Palette,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Aparência",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                ExposedDropdownMenuBox(expanded, { expanded = it }) {
                    OutlinedTextField(
                        value = when (theme) {
                            "light" -> "Claro"
                            "dark" -> "Escuro"
                            else -> "Automático"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tema") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        listOf(
                            "system" to "Automático",
                            "light" to "Claro",
                            "dark" to "Escuro"
                        ).forEach { (v, l) ->
                            DropdownMenuItem(
                                text = { Text(l) },
                                onClick = {
                                    onTheme(v)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Privacidade e armazenamento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Suas chaves e seu histórico ficam armazenados localmente neste aparelho. O PixPronto não consulta bancos nem confirma pagamentos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Sobre o PixPronto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Versão 0.2.0 · GNU GPL-3.0-or-later")
                Text(
                    "Gerador local de QR Codes Pix.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
