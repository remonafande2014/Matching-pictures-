package com.example.photogrouper

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Screen(vm) } } }
    }
}

@Composable
fun Screen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.scan()
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تجميع الصور المتشابهة", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = { launcher.launch(permission) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
            Text("ابدأ الفحص")
        }

        if (s.busy && s.total > 0) {
            LinearProgressIndicator(
                progress = { s.progress / s.total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${s.progress} / ${s.total}")
        }

        if (s.groups.isNotEmpty()) {
            Text("حد التشابه: ${(s.threshold * 100).roundToInt()}%  (أعلى = مجموعات أدق وأقل)")
            Slider(value = s.threshold, onValueChange = vm::setThreshold, valueRange = 0.3f..0.95f)
            Button(onClick = vm::saveFolders, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
                Text("أنشئ الفولدرات (نسخ الصور)")
            }
        }

        if (s.message.isNotEmpty()) Text(s.message, color = MaterialTheme.colorScheme.primary)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(s.groups, key = { it.folder }) { g ->
                Text("${g.folder}  (${g.photos.size})", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(g.photos.take(20), key = { it.id }) { Thumb(it) }
                }
            }
        }
    }
}

@Composable
fun Thumb(p: Photo) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, p.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(p.uri, Size(160, 160), null) }.getOrNull()
        }
    }
    Box(Modifier.size(72.dp).background(Color.LightGray)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
