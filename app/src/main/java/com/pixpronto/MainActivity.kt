package com.pixpronto

import androidx.compose.foundation.isSystemInDarkTheme
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.pixpronto.data.PixDatabase
import com.pixpronto.data.PixHistoryEntity
import com.pixpronto.data.PixKeyEntity
import com.pixpronto.pix.PixPayload
import com.pixpronto.pix.PixRequest
import com.pixpronto.pix.QrCode
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = PixDatabase.get(this)
        setContent { PixProntoTheme { PixProntoRoot(db) } }
    }
}

@Composable
private fun PixProntoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF80D6A2), secondary = androidx.compose.ui.graphics.Color(0xFFB4DCC0))
    else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087A43), secondary = androidx.compose.ui.graphics.Color(0xFF38664B), background = androidx.compose.ui.graphics.Color(0xFFF7FAF7))
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

@Composable
private fun PixProntoRoot(db: PixDatabase) {
    var selected by remember { mutableStateOf(0) }
    val dao = db.pixDao()
    val history by dao.history().collectAsState(initial = emptyList())
    val keys by dao.keys().collectAsState(initial = emptyList())
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Gerar", "Histórico", "Chaves", "Sobre").forEachIndexed { index, label ->
                    val icon = when (index) { 0 -> Icons.Rounded.QrCode2; 1 -> Icons.Rounded.History; 2 -> Icons.Rounded.VpnKey; else -> Icons.Rounded.Settings }
                    NavigationBarItem(selected == index, onClick = { selected = index }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> GenerateScreen(Modifier.padding(padding), keys, dao)
            1 -> HistoryScreen(Modifier.padding(padding), history)
            2 -> KeysScreen(Modifier.padding(padding), keys, dao)
            else -> AboutScreen(Modifier.padding(padding))
        }
    }
}

@Composable
private fun GenerateScreen(modifier: Modifier, keys: List<PixKeyEntity>, dao: com.pixpronto.data.PixDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var beneficiary by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("CAMPINA GRANDE") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(keys) {
        keys.firstOrNull { it.isDefault }?.let { if (key.isBlank()) { key = it.value; beneficiary = it.beneficiary; city = it.city } }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("PixPronto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Seu PIX, do seu jeito.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(key, { key = it; payload = null }, label = { Text("Chave Pix") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(beneficiary, { beneficiary = it; payload = null }, label = { Text("Nome do beneficiário") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(city, { city = it.uppercase(Locale("pt", "BR")); payload = null }, label = { Text("Cidade") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(amount, { amount = it.replace(',', '.'); payload = null }, label = { Text("Valor (opcional — deixe vazio para valor livre)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(description, { description = it; payload = null }, label = { Text("Descrição (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {
            try {
                val value = amount.trim().takeIf { it.isNotEmpty() }?.let { BigDecimal(it) }
                val generated = PixPayload.build(PixRequest(key, beneficiary, city, value, description))
                qr = QrCode.bitmap(generated)
                payload = generated
                error = null
                scope.launch { dao.insertHistory(PixHistoryEntity(payload = generated, beneficiary = beneficiary, amount = value?.toPlainString(), description = description.ifBlank { null })) }
            } catch (e: Exception) { error = e.message ?: "Não foi possível gerar o QR Code."; payload = null; qr = null }
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Gerar QR Code") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        qr?.let { bitmap ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap.asImageBitmap(), contentDescription = "QR Code Pix gerado", modifier = Modifier.size(230.dp))
                    Text(beneficiary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (amount.isNotBlank()) Text(runCatching { NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(BigDecimal(amount)) }.getOrDefault(amount))
                    Text("QR Code gerado · pagamento não confirmado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { copyText(context, payload.orEmpty()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copiar Pix Copia e Cola") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun copyText(context: Context, value: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Pix Copia e Cola", value))
        Toast.makeText(context, "Pix copiado", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "Não foi possível copiar o Pix", Toast.LENGTH_LONG).show() }
}

@Composable
private fun HistoryScreen(modifier: Modifier, history: List<PixHistoryEntity>) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Histórico", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Códigos gerados neste aparelho", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) Text("Ainda não há QR Codes gerados.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.beneficiary, fontWeight = FontWeight.SemiBold)
                        Text(item.amount?.let { "Valor: R$ $it" } ?: "Valor livre")
                        Text(item.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text("${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date(item.createdAt))}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeysScreen(modifier: Modifier, keys: List<PixKeyEntity>, dao: com.pixpronto.data.PixDao) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CPF") }
    var beneficiary by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("CAMPINA GRANDE") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Minhas chaves", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(label, { label = it }, label = { Text("Apelido") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value, { value = it }, label = { Text("Chave Pix") }, modifier = Modifier.fillMaxWidth())
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded, { expanded = it }) {
            OutlinedTextField(type, {}, readOnly = true, label = { Text("Tipo") }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded, { expanded = false }) { listOf("CPF", "CNPJ", "E-mail", "Telefone", "Aleatória").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { type = it; expanded = false }) } }
        }
        OutlinedTextField(beneficiary, { beneficiary = it }, label = { Text("Beneficiário") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(city, { city = it }, label = { Text("Cidade") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (label.isNotBlank() && value.isNotBlank() && beneficiary.isNotBlank() && city.isNotBlank()) {
                scope.launch { runCatching { dao.insertKey(PixKeyEntity(label = label, type = type, value = value, beneficiary = beneficiary, city = city, isDefault = keys.isEmpty())) } }
                label = ""; value = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar chave") }
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(keys, key = { it.id }) { item ->
                ListItem(headlineContent = { Text(item.label) }, supportingContent = { Text("${item.type} · ${item.value.take(3)}••••") }, trailingContent = { TextButton(onClick = { scope.launch { dao.deleteKey(item) } }) { Text("Excluir") } })
            }
        }
    }
}

@Composable
private fun AboutScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sobre o PixPronto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Gerador local de QR Codes Pix. Seus dados permanecem neste aparelho.")
        Text("O aplicativo não consulta bancos, não verifica a existência de chaves e não confirma pagamentos.")
        Text("Versão 0.1.0 · GNU GPL-3.0-or-later")
    }
}
