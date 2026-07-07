package com.stylefit.tryon

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.stylefit.tryon.network.GradioClient
import com.stylefit.tryon.network.TryOnPresets
import com.stylefit.tryon.ui.theme.StyleFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StyleFitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StyleFitApp()
                }
            }
        }
    }
}

private const val PREFS = "stylefit_prefs"
private const val KEY_BASE_URL = "base_url"
private const val KEY_API_NAME = "api_name"

@Composable
fun StyleFitApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var personUri by remember { mutableStateOf<Uri?>(null) }
    var garmentUri by remember { mutableStateOf<Uri?>(null) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    var baseUrl by remember { mutableStateOf(prefs.getString(KEY_BASE_URL, TryOnPresets.presets[0].baseUrl)!!) }
    var apiName by remember { mutableStateOf(prefs.getString(KEY_API_NAME, TryOnPresets.presets[0].apiName)!!) }

    // --- camera capture temp uri holders ---
    var pendingCameraTarget by remember { mutableStateOf(0) } // 1 = person, 2 = garment
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val personGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { personUri = uri; resultUrl = null; errorMessage = null }
    }
    val garmentGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { garmentUri = uri; resultUrl = null; errorMessage = null }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCameraUri != null) {
            if (pendingCameraTarget == 1) personUri = pendingCameraUri
            if (pendingCameraTarget == 2) garmentUri = pendingCameraUri
            resultUrl = null
            errorMessage = null
        }
    }

    fun launchCamera(target: Int) {
        val uri = createImageCaptureUri(context)
        pendingCameraUri = uri
        pendingCameraTarget = target
        cameraLauncher.launch(uri)
    }

    if (showSettings) {
        SettingsScreen(
            currentBaseUrl = baseUrl,
            currentApiName = apiName,
            onSave = { newBase, newApi ->
                baseUrl = newBase
                apiName = newApi
                prefs.edit().putString(KEY_BASE_URL, newBase).putString(KEY_API_NAME, newApi).apply()
                showSettings = false
            },
            onBack = { showSettings = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("StyleFit", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("AI virtual try-on", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(24.dp))

        PhotoPickerCard(
            title = "1. Your photo",
            uri = personUri,
            onGallery = { personGalleryLauncher.launch("image/*") },
            onCamera = { launchCamera(1) }
        )

        Spacer(Modifier.height(16.dp))

        PhotoPickerCard(
            title = "2. Clothing photo",
            uri = garmentUri,
            onGallery = { garmentGalleryLauncher.launch("image/*") },
            onCamera = { launchCamera(2) }
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                errorMessage = null
                resultUrl = null
                isLoading = true
                scope.launch {
                    try {
                        val client = GradioClient(baseUrl, apiName)
                        val url = withContext(Dispatchers.IO) {
                            val personPath = client.uploadFile(context, personUri!!)
                            val garmentPath = client.uploadFile(context, garmentUri!!)
                            client.runTryOn(personPath, garmentPath)
                        }
                        resultUrl = url
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Something went wrong. Please try again."
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = personUri != null && garmentUri != null && !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Generating outfit… (can take up to a minute)")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Try It On")
            }
        }

        errorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(msg, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        resultUrl?.let { url ->
            Spacer(Modifier.height(24.dp))
            Text("Result", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = url,
                contentDescription = "Try-on result",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { saveImageFromUrl(context, url) }
                            Toast.makeText(context, "Saved to Pictures/StyleFit", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Powered by a free, community-hosted AI model. Results may take a little while, and the model can occasionally be busy — you can switch models anytime in Settings.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PhotoPickerCard(title: String, uri: Uri?, onGallery: () -> Unit, onCamera: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Photo, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    currentBaseUrl: String,
    currentApiName: String,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(currentBaseUrl) }
    var apiName by remember { mutableStateOf(currentApiName) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("AI model", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        TryOnPresets.presets.forEach { preset ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (preset.baseUrl == baseUrl)
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preset.baseUrl == baseUrl,
                        onClick = { baseUrl = preset.baseUrl; apiName = preset.apiName }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(preset.label)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Advanced (custom Space URL)", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Space base URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiName,
            onValueChange = { apiName = it },
            label = { Text("API name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onSave(baseUrl, apiName) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}

private fun createImageCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun saveImageFromUrl(context: Context, url: String) {
    val client = okhttp3.OkHttpClient()
    val request = okhttp3.Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
        val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty response")
        val fileName = "StyleFit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val outDir = File(picturesDir, "StyleFit").apply { mkdirs() }
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { it.write(bytes) }
    }
}
