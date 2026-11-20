package com.example.jiyu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

fun genreColor(g: String): Color = when {
    g.startsWith("動物") -> Color(0xFFFFE0B2)
    g.startsWith("自然") -> Color(0xFFC8E6C9)
    g.startsWith("宇宙") -> Color(0xFFD1C4E9)
    g.startsWith("科学") -> Color(0xFFB3E5FC)
    g.startsWith("歴史") -> Color(0xFFFFF59D)
    g.startsWith("世界") -> Color(0xFFF8BBD0)
    g.startsWith("乗り物") -> Color(0xFFB2EBF2)
    else -> Color(0xFFFFCCBC)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { seed(ctx, dao); updateFromWeb(ctx, dao) }
    }
    BackHandler(enabled = openId != null) { openId = null }

    val id = openId
    if (id != null) {
        DetailScreen(dao, id) { openId = null }
        return
    }
    Scaffold(
        containerColor = Color(0xFFFFFDF5),
        bottomBar = {
            NavigationBar {
                listOf("🏠" to "テーマ", "❤️" to "お気に入り", "📖" to "きろく").forEachIndexed { i, (e, l) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(e, fontSize = 28.sp) },
                        label = { Text(l, fontSize = 14.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            val flow = remember(tab) { when (tab) { 0 -> dao.current(); 1 -> dao.favorites(); else -> dao.history() } }
            val list by flow.collectAsState(initial = emptyList())
            Text(
                when (tab) { 0 -> "今日の自由学習、何を調べる？"; 1 -> "お気に入りのテーマ"; else -> "調べたテーマ" },
                fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                if (list.isEmpty() && tab != 0) item { Text("まだありません", fontSize = 20.sp) }
                items(list, key = { it.id }) { t -> ThemeCard(t) { openId = t.id } }
                if (tab == 0) item {
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { newBatch(dao) } },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("🔄 ほかのテーマを見る", fontSize = 22.sp) }
                }
            }
        }
    }
}

@Composable
fun ThemeCard(t: Theme, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = genreColor(t.genre)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.emoji, fontSize = 44.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(t.genre, fontSize = 14.sp, color = Color(0xFF555555))
                Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
fun DetailScreen(dao: ThemeDao, id: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val t by remember(id) { dao.byId(id) }.collectAsState(initial = null)
    LaunchedEffect(id) { withContext(Dispatchers.IO) { dao.setViewed(id, System.currentTimeMillis()) } }
    val th = t ?: return
    Column(
        Modifier.fillMaxSize().background(Color(0xFFFFFDF5)).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.height(56.dp)) { Text("⬅ もどる", fontSize = 20.sp) }
        Box(
            Modifier.fillMaxWidth().height(160.dp).background(genreColor(th.genre), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { Text(th.emoji, fontSize = 96.sp) }
        Text(th.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(th.explanation, fontSize = 22.sp, lineHeight = 36.sp)
        if (th.summary.isNotBlank()) Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF59D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("📝 ノートのポイント", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(th.summary, fontSize = 20.sp, lineHeight = 30.sp)
            }
        }
        if (th.source.isNotBlank()) Text("📚 ${th.source}", fontSize = 16.sp, color = Color(0xFF555555))
        Button(
            onClick = { scope.launch(Dispatchers.IO) { dao.setFav(th.id, !th.favorite) } },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text(if (th.favorite) "💔 お気に入りをやめる" else "❤️ お気に入りにする", fontSize = 22.sp) }
        Text("ノートに自分のことばでまとめよう！", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
    }
}
