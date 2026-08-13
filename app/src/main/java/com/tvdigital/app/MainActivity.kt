package com.tvdigital.app

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tvdigital.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvDigitalApp() }
    }
}

private val Bg = Color(0xFF05070A)
private val Panel = Color(0xFF101722)
private val Panel2 = Color(0xFF172333)
private val Blue = Color(0xFF2C8CFF)
private val Muted = Color(0xFFA8B7C9)

@Composable
fun TvDigitalApp(){
    MaterialTheme(colorScheme=darkColorScheme(primary=Blue,background=Bg,surface=Panel)){
        var token by remember { mutableStateOf<String?>(null) }
        var user by remember { mutableStateOf<UserDto?>(null) }
        var playing by remember { mutableStateOf<ContentDto?>(null) }

        // Mantiene viva la sesión para aplicar correctamente el límite de conexiones simultáneas.
        LaunchedEffect(token) {
            val current = token ?: return@LaunchedEffect
            while (isActive) {
                delay(45_000)
                try { ApiProvider.api.ping("Bearer $current") } catch (_: Exception) { }
            }
        }

        when {
            playing != null -> PlayerScreen(playing!!){ playing = null }
            token == null -> LoginScreen { t, u -> token = t; user = u }
            else -> HomeScreen(
                token = token!!,
                user = user!!,
                onPlay = { playing = it },
                onLogout = {
                    val old = token
                    token = null
                    user = null
                    if (old != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try { ApiProvider.api.logout("Bearer $old") } catch (_: Exception) { }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun LoginScreen(onLogin:(String,UserDto)->Unit){
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val deviceKey = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "android-unknown"
    }

    Box(Modifier.fillMaxSize().background(Bg).padding(28.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 980.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.tv_digital_logo),
                    contentDescription = "Logo TV DIGITAL",
                    modifier = Modifier.size(245.dp),
                    contentScale = ContentScale.Fit
                )
                Text("TV DIGITAL", fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text("Televisión · Películas · Series", color = Muted)
            }
            Spacer(Modifier.width(42.dp))
            Card(
                Modifier.weight(1f).widthIn(max = 430.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(28.dp)) {
                    Text("Iniciar sesión", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Ingresa los datos proporcionados por TV DIGITAL", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(22.dp))
                    OutlinedTextField(username, { username = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            scope.launch {
                                busy = true; msg = ""
                                try {
                                    val r = ApiProvider.api.login(LoginRequest(username.trim(), password, deviceKey, android.os.Build.MODEL))
                                    onLogin(r.token, r.user)
                                } catch (e: Exception) {
                                    msg = "No se pudo ingresar. Verifica tu cuenta o conexión."
                                } finally { busy = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text(if (busy) "CONECTANDO…" else "INGRESAR", fontWeight = FontWeight.Bold) }
                    if (msg.isNotBlank()) {
                        Spacer(Modifier.height(12.dp)); Text(msg, color = Color(0xFFFF8A80), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(token:String,user:UserDto,onPlay:(ContentDto)->Unit,onLogout:()->Unit){
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ContentDto>>(emptyList()) }
    var filter by remember { mutableStateOf("LIVE") }
    var query by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<String>()) }

    fun load(type:String){
        filter = type
        scope.launch {
            try { items = ApiProvider.api.content("Bearer $token", type); msg = "" }
            catch (_:Exception) { msg = "No se pudo cargar el contenido." }
        }
    }
    LaunchedEffect(Unit){ load("LIVE") }
    val visibleItems = remember(items, query) {
        if (query.isBlank()) items else items.filter { it.title.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 26.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.tv_digital_logo), "TV DIGITAL", Modifier.size(58.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("TV DIGITAL", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("${user.name} · ${if (user.role == "TRIAL") "PRUEBA 24 H" else "CLIENTE"}", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Buscar…") }, singleLine = true,
                modifier = Modifier.width(250.dp).heightIn(min = 52.dp)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onLogout) { Text("SALIR") }
        }

        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("LIVE" to "TV EN VIVO", "MOVIE" to "PELÍCULAS", "SERIES" to "SERIES").forEach { (key,label) ->
                Button(
                    onClick = { load(key) },
                    colors = ButtonDefaults.buttonColors(containerColor = if(filter == key) Blue else Panel2),
                    modifier = Modifier.height(50.dp).focusable()
                ) { Text(label, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.weight(1f))
            Text("${visibleItems.size} disponibles", color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
        }

        if (msg.isNotBlank()) Text(msg, color = Color(0xFFFFB4AB), modifier = Modifier.padding(horizontal = 24.dp))

        if (visibleItems.isEmpty() && msg.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "Todavía no hay contenido en esta sección." else "No se encontraron resultados.", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 230.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                items(visibleItems, key = { it.id }) { c ->
                    ContentCard(
                        c,
                        favorite = favorites.contains(c.id),
                        onFavorite = { favorites = if (favorites.contains(c.id)) favorites - c.id else favorites + c.id },
                        onClick = { onPlay(c) }
                    )
                }
            }
        }
    }
}

@Composable
fun ContentCard(c:ContentDto,favorite:Boolean,onFavorite:()->Unit,onClick:()->Unit){
    Card(
        Modifier.fillMaxWidth().height(160.dp).clickable { onClick() }.focusable(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Box(Modifier.fillMaxSize().background(Panel2).padding(18.dp)) {
            Column(Modifier.align(Alignment.BottomStart).padding(end = 42.dp)) {
                Text(c.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    when(c.type){"LIVE"->"TV EN VIVO";"MOVIE"->"PELÍCULA";else->"SERIE"} + (c.year?.let { " · $it" } ?: ""),
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            Text(
                if (favorite) "★" else "☆",
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.TopEnd).clickable { onFavorite() }.padding(4.dp)
            )
        }
    }
}

@Composable
fun PlayerScreen(item:ContentDto,onBack:()->Unit){
    val context = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player){ onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.align(Alignment.TopStart).padding(20.dp)) {
            Button(onClick = onBack) { Text("← VOLVER") }
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
