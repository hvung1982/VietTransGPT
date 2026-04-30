package com.example.doc2chatgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.doc2chatgpt.ui.theme.Doc2ChatGPTTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class DetailItem {
    abstract val title: String

    data class PdfPage(val uri: Uri, val index: Int) : DetailItem() {
        override val title: String = "Trang ${index + 1}"
    }

    data class Image(val uri: Uri, val index: Int) : DetailItem() {
        override val title: String = "Ảnh ${index + 1}"
    }
}

class MainActivity : ComponentActivity() {

    private val defaultPrompt = """
Dịch toàn bộ ảnh sang tiếng Việt chuẩn học thuật toán/vật lý.
Sau đó tạo lại một ảnh mới CHỈ CÓ TIẾNG VIỆT.
Không song ngữ, xóa toàn bộ chữ gốc, giữ công thức tuyệt đối.
Nội dung chính xác quan trọng hơn layout.

Thuật ngữ bắt buộc:
контакт = tiếp xúc
плотность тока = mật độ dòng điện
сопротивление = điện trở
удельное сопротивление = điện trở suất
напряжение = điện áp
мощность = công suất
нагрев = sự gia nhiệt
перегрев = quá nhiệt
""".trimIndent()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pdfRenderer = PdfPageRenderer(this)
        val promptStore = PromptStore(this)

        setContent {
            Doc2ChatGPTTheme {
                var prompt by remember { mutableStateOf(promptStore.getPrompt(defaultPrompt)) }
                var pdfUri by remember { mutableStateOf<Uri?>(null) }
                var pageCount by remember { mutableIntStateOf(0) }
                var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                var status by remember { mutableStateOf("Chưa chọn tài liệu") }
                var isBusy by remember { mutableStateOf(false) }
                var selectedItem by remember { mutableStateOf<DetailItem?>(null) }

                LaunchedEffect(prompt) {
                    promptStore.savePrompt(prompt)
                }

                val pickPdf = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult

                    lifecycleScope.launch {
                        isBusy = true
                        status = "Đang đọc PDF..."
                        try {
                            val count = withContext(Dispatchers.IO) {
                                ShareHelper.clearSharedCache(this@MainActivity)
                                pdfRenderer.getPageCount(uri)
                            }
                            pdfUri = uri
                            imageUris = emptyList()
                            pageCount = count
                            selectedItem = null
                            status = "Đã chọn PDF: $count trang"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            pdfUri = null
                            pageCount = 0
                            selectedItem = null
                            status = "Lỗi đọc PDF: ${e.message ?: "không rõ nguyên nhân"}"
                        } finally {
                            isBusy = false
                        }
                    }
                }

                val pickImages = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetMultipleContents(),
                ) { uris ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            ShareHelper.clearSharedCache(this@MainActivity)
                        }
                        pdfUri = null
                        pageCount = 0
                        imageUris = uris
                        selectedItem = null
                        status = "Đã chọn ${uris.size} ảnh"
                    }
                }

                Doc2ChatGPTScreen(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    pdfUri = pdfUri,
                    pageCount = pageCount,
                    imageUris = imageUris,
                    status = status,
                    isBusy = isBusy,
                    selectedItem = selectedItem,
                    pdfRenderer = pdfRenderer,
                    onPickPdf = { pickPdf.launch("application/pdf") },
                    onPickImages = { pickImages.launch("image/*") },
                    onCopyPrompt = {
                        copyPromptToClipboard(prompt)
                        status = "Đã copy prompt"
                    },
                    onSelectItem = { selectedItem = it },
                    onBackToList = { selectedItem = null },
                    onShare = { item ->
                        isBusy = true
                        shareItem(item, this@MainActivity, pdfRenderer, prompt) { result ->
                            status = result
                            isBusy = false
                        }
                    },
                    onStatusUpdate = { status = it }
                )
            }
        }
    }

    private fun copyPromptToClipboard(prompt: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("VietTransGPT prompt", prompt))
    }

    private fun shareItem(
        item: DetailItem,
        activity: ComponentActivity,
        pdfRenderer: PdfPageRenderer,
        prompt: String,
        onComplete: (String) -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                val cachedImage = withContext(Dispatchers.IO) {
                    when (item) {
                        is DetailItem.PdfPage -> ShareHelper.CachedImage(
                            file = pdfRenderer.renderPageToPng(item.uri, item.index, scale = 2.0f),
                            mimeType = "image/png"
                        )
                        is DetailItem.Image -> ShareHelper.copyUriToCache(activity, item.uri)
                    }
                }
                ShareHelper.shareImageWithPrompt(
                    context = activity,
                    imageFile = cachedImage.file,
                    prompt = prompt,
                    mimeType = cachedImage.mimeType
                )
                copyPromptToClipboard(prompt)
                onComplete("Đã copy prompt và mở share ${item.title.lowercase()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete("Lỗi share: ${e.message ?: "không rõ nguyên nhân"}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Doc2ChatGPTScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    pdfUri: Uri?,
    pageCount: Int,
    imageUris: List<Uri>,
    status: String,
    isBusy: Boolean,
    selectedItem: DetailItem?,
    pdfRenderer: PdfPageRenderer,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onCopyPrompt: () -> Unit,
    onSelectItem: (DetailItem) -> Unit,
    onBackToList: () -> Unit,
    onShare: (DetailItem) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                ListPane(
                    modifier = Modifier
                        .width(420.dp)
                        .fillMaxHeight(),
                    prompt = prompt,
                    onPromptChange = onPromptChange,
                    pdfUri = pdfUri,
                    pageCount = pageCount,
                    imageUris = imageUris,
                    status = status,
                    isBusy = isBusy,
                    pdfRenderer = pdfRenderer,
                    onPickPdf = onPickPdf,
                    onPickImages = onPickImages,
                    onCopyPrompt = onCopyPrompt,
                    onSelectItem = onSelectItem,
                    onShare = onShare
                )
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                DetailPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    item = selectedItem,
                    pdfRenderer = pdfRenderer,
                    isBusy = isBusy,
                    showBack = false,
                    onBack = onBackToList,
                    onShare = onShare,
                    onStatusUpdate = onStatusUpdate
                )
            }
        } else if (selectedItem == null) {
            ListPane(
                modifier = Modifier.fillMaxSize(),
                prompt = prompt,
                onPromptChange = onPromptChange,
                pdfUri = pdfUri,
                pageCount = pageCount,
                imageUris = imageUris,
                status = status,
                isBusy = isBusy,
                pdfRenderer = pdfRenderer,
                onPickPdf = onPickPdf,
                onPickImages = onPickImages,
                onCopyPrompt = onCopyPrompt,
                onSelectItem = onSelectItem,
                onShare = onShare
            )
        } else {
            BackHandler(onBack = onBackToList)
            DetailPane(
                modifier = Modifier.fillMaxSize(),
                item = selectedItem,
                pdfRenderer = pdfRenderer,
                isBusy = isBusy,
                showBack = true,
                onBack = onBackToList,
                onShare = onShare,
                onStatusUpdate = onStatusUpdate
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPane(
    modifier: Modifier,
    prompt: String,
    onPromptChange: (String) -> Unit,
    pdfUri: Uri?,
    pageCount: Int,
    imageUris: List<Uri>,
    status: String,
    isBusy: Boolean,
    pdfRenderer: PdfPageRenderer,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onCopyPrompt: () -> Unit,
    onSelectItem: (DetailItem) -> Unit,
    onShare: (DetailItem) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "VietTransGPT",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Dịch tài liệu toán - lý bằng ChatGPT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            item("controls") {
                MainControls(
                    prompt = prompt,
                    onPromptChange = onPromptChange,
                    controlsEnabled = !isBusy,
                    onPickPdf = onPickPdf,
                    onPickImages = onPickImages,
                    onCopyPrompt = onCopyPrompt,
                    status = status
                )
            }

            if ((pdfUri != null && pageCount > 0) || imageUris.isNotEmpty()) {
                item("divider") {
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            if (pdfUri != null && pageCount > 0) {
                items(count = pageCount, key = { "pdf_$it" }) { index ->
                    PdfPageRow(
                        pdfUri = pdfUri,
                        pageIndex = index,
                        enabled = !isBusy,
                        pdfRenderer = pdfRenderer,
                        onClick = { onSelectItem(DetailItem.PdfPage(pdfUri, index)) },
                        onShare = { onShare(DetailItem.PdfPage(pdfUri, index)) }
                    )
                }
            } else if (imageUris.isNotEmpty()) {
                itemsIndexed(
                    items = imageUris,
                    key = { index, uri -> "img_${uri}_$index" }
                ) { index, uri ->
                    ImageRow(
                        uri = uri,
                        imageIndex = index,
                        enabled = !isBusy,
                        onClick = { onSelectItem(DetailItem.Image(uri, index)) },
                        onShare = { onShare(DetailItem.Image(uri, index)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPane(
    modifier: Modifier,
    item: DetailItem?,
    pdfRenderer: PdfPageRenderer,
    isBusy: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onShare: (DetailItem) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        item?.title ?: "Xem trước",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(enabled = !isBusy, onClick = { onShare(item) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (item != null) {
                DetailPreview(
                    item = item,
                    pdfRenderer = pdfRenderer,
                    onStatusUpdate = onStatusUpdate
                )
            } else {
                EmptyPreview()
            }
        }
    }
}

@Composable
private fun EmptyPreview() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.ZoomIn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Chọn một trang hoặc ảnh để xem trước",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun DetailPreview(
    item: DetailItem,
    pdfRenderer: PdfPageRenderer,
    onStatusUpdate: (String) -> Unit
) {
    var imageFile by remember(item) { mutableStateOf<File?>(null) }
    var isLoading by remember(item) { mutableStateOf(true) }

    LaunchedEffect(item) {
        isLoading = true
        try {
            imageFile = withContext(Dispatchers.IO) {
                when (item) {
                    is DetailItem.PdfPage -> pdfRenderer.renderPageToPng(item.uri, item.index, scale = 1.5f)
                    is DetailItem.Image -> null
                }
            }
        } catch (e: Exception) {
            onStatusUpdate("Lỗi xem trước: ${e.message ?: "không rõ nguyên nhân"}")
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            val model: Any? = when (item) {
                is DetailItem.PdfPage -> imageFile
                is DetailItem.Image -> item.uri
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun MainControls(
    prompt: String,
    onPromptChange: (String) -> Unit,
    controlsEnabled: Boolean,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onCopyPrompt: () -> Unit,
    status: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Prompt mặc định",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        enabled = controlsEnabled,
                        onClick = onCopyPrompt,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy prompt")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = controlsEnabled,
                onClick = onPickPdf,
                contentPadding = PaddingValues(14.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Chọn PDF")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = controlsEnabled,
                onClick = onPickImages,
                contentPadding = PaddingValues(14.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Chọn ảnh")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun PdfPageRow(
    pdfUri: Uri,
    pageIndex: Int,
    enabled: Boolean,
    pdfRenderer: PdfPageRenderer,
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    var thumbnailFile by remember(pdfUri, pageIndex) { mutableStateOf<File?>(null) }

    LaunchedEffect(pdfUri, pageIndex) {
        thumbnailFile = try {
            withContext(Dispatchers.IO) {
                pdfRenderer.renderPageToPng(pdfUri, pageIndex, scale = 0.2f, cachePrefix = "thumb")
            }
        } catch (_: Exception) {
            null
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ThumbnailBox {
                    if (thumbnailFile != null) {
                        AsyncImage(
                            model = thumbnailFile,
                            contentDescription = "Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Trang ${pageIndex + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            FilledTonalButton(
                enabled = enabled,
                onClick = onShare,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun ImageRow(
    uri: Uri,
    imageIndex: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ThumbnailBox {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Ảnh ${imageIndex + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            FilledTonalButton(
                enabled = enabled,
                onClick = onShare,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun ThumbnailBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
        content = content
    )
}
