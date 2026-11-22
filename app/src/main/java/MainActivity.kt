package com.kriti.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(this, "kriti_secure_settings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        setContent { KritiApp(prefs) }
    }
}

data class ChatLine(val text: String, val user: Boolean)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KritiApp(prefs: android.content.SharedPreferences) {
    val rose = Color(0xFFCF6590)
    var chat by remember { mutableStateOf(listOf(ChatLine("Hello! I'm Kriti. Open Settings to connect your AI provider and customize my personality.", false))) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf(prefs.getString("base", "https://api.groq.com/openai/v1") ?: "") }
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "llama-3.3-70b-versatile") ?: "") }
    var persona by remember { mutableStateOf(prefs.getString("persona", "You are Kriti, a helpful, warm, respectful personal AI assistant. Be honest, concise, and thoughtful.") ?: "") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val http = remember { OkHttpClient() }
    MaterialTheme(colorScheme = lightColorScheme(primary = rose, secondary = Color(0xFF8D6AA6))) {
        Scaffold(topBar = {
            TopAppBar(title = { Column { Text("Kriti", fontWeight = FontWeight.Bold); Text(if (showSettings) "Personal settings" else "Personal AI assistant", style = MaterialTheme.typography.labelSmall) } },
                actions = { TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Chat" else "Settings") } })
        }) { padding ->
            if (showSettings) {
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI provider connection", fontWeight = FontWeight.Bold)
                    OutlinedTextField(base, { base = it }, label = { Text("API base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Personality instructions", fontWeight = FontWeight.Bold)
                    OutlinedTextField(persona, { persona = it }, label = { Text("Kriti's persona") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Button(onClick = {
                        prefs.edit().putString("base", base.trim()).putString("key", key.trim()).putString("model", model.trim()).putString("persona", persona).apply()
                        showSettings = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    Text("API usage depends on the selected provider. Free tiers may have rate limits; charges may apply.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chat) { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.user) Arrangement.End else Arrangement.Start) {
                                Surface(color = if (item.user) rose.copy(alpha = .14f) else Color(0xFFF2EFF4), shape = RoundedCornerShape(18.dp)) {
                                    Text(item.text, Modifier.padding(13.dp).widthIn(max = 300.dp))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Kriti…") }, maxLines = 4)
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = draft.isNotBlank() && !busy, onClick = {
                            val msg = draft.trim(); draft = ""; chat = chat + ChatLine(msg, true); busy = true
                            scope.launch {
                                try {
                                    val reply = withContext(Dispatchers.IO) { callProvider(http, base, key, model, persona, chat) }
                                    chat = chat + ChatLine(reply, false)
                                } catch (e: Exception) {
                                    chat = chat + ChatLine("Connection failed: ${e.message ?: "Please check settings and internet"}", false)
                                } finally { busy = false }
                            }
                        }) { Text(if (busy) "…" else "Send") }
                    }
                }
            }
        }
    }
}


private fun callProvider(client: OkHttpClient, base: String, key: String, model: String, persona: String, chat: List<ChatLine>): String {
    require(key.isNotBlank()) { "Add your API key in Settings first." }
    require(model.isNotBlank()) { "Model ID is missing." }
    val url = base.trim().trimEnd('/') + "/chat/completions"
    val messages = JSONArray().put(JSONObject().put("role", "system").put("content", persona))
    chat.forEach { messages.put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
    val payload = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.7)
    val request = Request.Builder().url(url).header("Authorization", "Bearer ${key.trim()}")
        .header("Content-Type", "application/json").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
    client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${body.take(220)}")
        JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
