package com.example.doc2chatgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.doc2chatgpt.ui.theme.Doc2ChatGPTTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

private fun DetailItem.savedKey(): String {
    return when (this) {
        is DetailItem.PdfPage -> "pdf:$index"
        is DetailItem.Image -> "image:$index"
    }
}

private fun restoreSelectedItem(
    key: String?,
    pdfUri: Uri?,
    pageCount: Int,
    imageUris: List<Uri>
): DetailItem? {
    if (key == null) return null
    val parts = key.split(":", limit = 2)
    val type = parts.getOrNull(0)
    val index = parts.getOrNull(1)?.toIntOrNull() ?: return null

    return when {
        type == "pdf" && pdfUri != null && index in 0 until pageCount -> {
            DetailItem.PdfPage(pdfUri, index)
        }
        type == "image" && index in imageUris.indices -> {
            DetailItem.Image(imageUris[index], index)
        }
        else -> null
    }
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val pdfRenderer = PdfPageRenderer(this)
        val promptStore = PromptStore(this)

        setContent {
            Doc2ChatGPTTheme {
                var prompt1 by remember { mutableStateOf(promptStore.getPromptSlot(1, "")) }
                var prompt2 by remember { mutableStateOf(promptStore.getPromptSlot(2, "")) }
                var prompt3 by remember { mutableStateOf(promptStore.getPromptSlot(3, "")) }
                var pdfUriText by rememberSaveable { mutableStateOf<String?>(null) }
                var pageCount by rememberSaveable { mutableStateOf(0) }
                var imageUriTexts by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
                var status by rememberSaveable { mutableStateOf("Chưa chọn tài liệu") }
                var isBusy by remember { mutableStateOf(false) }
                var selectedItemKey by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingShareItem by remember { mutableStateOf<DetailItem?>(null) }
                val pdfUri = remember(pdfUriText) { pdfUriText?.let(Uri::parse) }
                val imageUris = remember(imageUriTexts) { imageUriTexts.map(Uri::parse) }
                val selectedItem = remember(pdfUri, pageCount, imageUris, selectedItemKey) {
                    restoreSelectedItem(selectedItemKey, pdfUri, pageCount, imageUris)
                }
                val listState = rememberLazyListState()
                LaunchedEffect(prompt1) { promptStore.savePromptSlot(1, prompt1) }
                LaunchedEffect(prompt2) { promptStore.savePromptSlot(2, prompt2) }
                LaunchedEffect(prompt3) { promptStore.savePromptSlot(3, prompt3) }

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
                            pdfUriText = uri.toString()
                            imageUriTexts = emptyList()
                            pageCount = count
                            selectedItemKey = null
                            status = "Đã chọn PDF: $count trang"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            pdfUriText = null
                            pageCount = 0
                            selectedItemKey = null
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
                        pdfUriText = null
                        pageCount = 0
                        imageUriTexts = uris.map(Uri::toString)
                        selectedItemKey = null
                        status = "Đã chọn ${uris.size} ảnh"
                    }
                }

                VietTransGPTScreen(
                    prompt1 = prompt1,
                    onPrompt1Change = { prompt1 = it },
                    prompt2 = prompt2,
                    onPrompt2Change = { prompt2 = it },
                    prompt3 = prompt3,
                    onPrompt3Change = { prompt3 = it },
                    pdfUri = pdfUri,
                    pageCount = pageCount,
                    imageUris = imageUris,
                    status = status,
                    isBusy = isBusy,
                    selectedItem = selectedItem,
                    listState = listState,
                    pdfRenderer = pdfRenderer,
                    onPickPdf = { pickPdf.launch("application/pdf") },
                    onPickImages = { pickImages.launch("image/*") },
                    onCopyPrompt1 = {
                        copyPromptToClipboard(prompt1)
                        status = "Đã copy prompt 1"
                    },
                    onCopyPrompt2 = {
                        copyPromptToClipboard(prompt2)
                        status = "Đã copy prompt 2"
                    },
                    onCopyPrompt3 = {
                        copyPromptToClipboard(prompt3)
                        status = "Đã copy prompt 3"
                    },
                    onCopyPromptBundle = {
                        copyPromptBundleToClipboard(prompt1, prompt2, prompt3)
                        status = "Đã nạp prompt 1/2/3 vào clipboard"
                    },
                    onSelectItem = { selectedItemKey = it.savedKey() },
                    onBackToList = { selectedItemKey = null },
                    onShare = { item ->
                        pendingShareItem = item
                    },
                    onStatusUpdate = { status = it }
                )

                if (pendingShareItem != null) {
                    val item = pendingShareItem
                    pendingShareItem = null
                    if (item != null) {
                        isBusy = true
                        shareItem(item, this@MainActivity, pdfRenderer, prompt1) { result ->
                            status = result
                            isBusy = false
                        }
                    }
                }
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
                if (activity is MainActivity) {
                    activity.copyPromptBundleToClipboard(
                        activity.findPromptSlot(1),
                        activity.findPromptSlot(2),
                        activity.findPromptSlot(3)
                    )
                }
                onComplete("Đã copy prompt và mở share ${item.title.lowercase()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete("Lỗi share: ${e.message ?: "không rõ nguyên nhân"}")
            }
        }
    }

    private fun copyPromptBundleToClipboard(prompt1: String, prompt2: String, prompt3: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        lifecycleScope.launch {
            // Copy ngược thứ tự với khoảng trễ để Android kịp lưu vào lịch sử (History)
            if (prompt3.isNotBlank()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Prompt 3", prompt3))
                delay(150)
            }
            if (prompt2.isNotBlank()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Prompt 2", prompt2))
                delay(150)
            }
            if (prompt1.isNotBlank()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Prompt 1", prompt1))
            }
        }
    }

    private fun findPromptSlot(slot: Int): String {
        return PromptStore(this).getPromptSlot(slot, "")
    }
}

@Composable
private fun VietTransGPTScreen(
    prompt1: String,
    onPrompt1Change: (String) -> Unit,
    prompt2: String,
    onPrompt2Change: (String) -> Unit,
    prompt3: String,
    onPrompt3Change: (String) -> Unit,
    pdfUri: Uri?,
    pageCount: Int,
    imageUris: List<Uri>,
    status: String,
    isBusy: Boolean,
    selectedItem: DetailItem?,
    listState: LazyListState,
    pdfRenderer: PdfPageRenderer,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onCopyPrompt1: () -> Unit,
    onCopyPrompt2: () -> Unit,
    onCopyPrompt3: () -> Unit,
    onCopyPromptBundle: () -> Unit,
    onSelectItem: (DetailItem) -> Unit,
    onBackToList: () -> Unit,
    onShare: (DetailItem) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        // Ngưỡng 600dp (Medium width) là tiêu chuẩn cho điện thoại gập khi mở ra hoặc tablet nhỏ
        val isWide = screenWidth >= 600.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                ListPane(
                    modifier = Modifier
                        .width(wideListPaneWidth(screenWidth))
                        .fillMaxHeight(),
                    wideMode = true,
                    prompt1 = prompt1,
                    onPrompt1Change = onPrompt1Change,
                    prompt2 = prompt2,
                    onPrompt2Change = onPrompt2Change,
                    prompt3 = prompt3,
                    onPrompt3Change = onPrompt3Change,
                    pdfUri = pdfUri,
                    pageCount = pageCount,
                    imageUris = imageUris,
                    status = status,
                    isBusy = isBusy,
                    listState = listState,
                    pdfRenderer = pdfRenderer,
                    onPickPdf = onPickPdf,
                    onPickImages = onPickImages,
                    onCopyPrompt1 = onCopyPrompt1,
                    onCopyPrompt2 = onCopyPrompt2,
                    onCopyPrompt3 = onCopyPrompt3,
                    onCopyPromptBundle = onCopyPromptBundle,
                    onSelectItem = onSelectItem,
                    onShare = onShare
                )
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
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
                wideMode = false,
                prompt1 = prompt1,
                onPrompt1Change = onPrompt1Change,
                prompt2 = prompt2,
                onPrompt2Change = onPrompt2Change,
                prompt3 = prompt3,
                onPrompt3Change = onPrompt3Change,
                pdfUri = pdfUri,
                pageCount = pageCount,
                imageUris = imageUris,
                status = status,
                isBusy = isBusy,
                listState = listState,
                pdfRenderer = pdfRenderer,
                onPickPdf = onPickPdf,
                onPickImages = onPickImages,
                onCopyPrompt1 = onCopyPrompt1,
                onCopyPrompt2 = onCopyPrompt2,
                onCopyPrompt3 = onCopyPrompt3,
                onCopyPromptBundle = onCopyPromptBundle,
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

private fun wideListPaneWidth(screenWidth: Dp): Dp {
    // Với màn hình không quá rộng (như điện thoại gập), cho phép list pane chiếm tỷ lệ lớn hơn một chút để dễ thao tác
    return if (screenWidth < 840.dp) {
        (screenWidth * 0.42f).coerceAtLeast(280.dp)
    } else {
        (screenWidth * 0.34f).coerceIn(320.dp, 400.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ListPane(
    modifier: Modifier,
    wideMode: Boolean,
    prompt1: String,
    onPrompt1Change: (String) -> Unit,
    prompt2: String,
    onPrompt2Change: (String) -> Unit,
    prompt3: String,
    onPrompt3Change: (String) -> Unit,
    pdfUri: Uri?,
    pageCount: Int,
    imageUris: List<Uri>,
    status: String,
    isBusy: Boolean,
    listState: LazyListState,
    pdfRenderer: PdfPageRenderer,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onCopyPrompt1: () -> Unit,
    onCopyPrompt2: () -> Unit,
    onCopyPrompt3: () -> Unit,
    onCopyPromptBundle: () -> Unit,
    onSelectItem: (DetailItem) -> Unit,
    onShare: (DetailItem) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (wideMode) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "VietTransGPT",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Dịch tài liệu toán - lý",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
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
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(if (wideMode) 8.dp else 12.dp),
            contentPadding = PaddingValues(
                start = if (wideMode) 12.dp else 16.dp,
                end = if (wideMode) 12.dp else 16.dp,
                bottom = 24.dp
            )
        ) {
            item("controls") {
                MainControls(
                    prompt1 = prompt1,
                    onPrompt1Change = onPrompt1Change,
                    prompt2 = prompt2,
                    onPrompt2Change = onPrompt2Change,
                    prompt3 = prompt3,
                    onPrompt3Change = onPrompt3Change,
                    controlsEnabled = !isBusy,
                    wideMode = wideMode,
                    onPickPdf = onPickPdf,
                    onPickImages = onPickImages,
                    onCopyPrompt1 = onCopyPrompt1,
                    onCopyPrompt2 = onCopyPrompt2,
                    onCopyPrompt3 = onCopyPrompt3,
                    onCopyPromptBundle = onCopyPromptBundle
                )
            }

            stickyHeader("status") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatusCard(
                        status = status,
                        wideMode = wideMode,
                        modifier = Modifier.padding(vertical = if (wideMode) 4.dp else 6.dp)
                    )
                }
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
                        wideMode = wideMode,
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
                        wideMode = wideMode,
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
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainControls(
    prompt1: String,
    onPrompt1Change: (String) -> Unit,
    prompt2: String,
    onPrompt2Change: (String) -> Unit,
    prompt3: String,
    onPrompt3Change: (String) -> Unit,
    controlsEnabled: Boolean,
    wideMode: Boolean,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onCopyPrompt1: () -> Unit,
    onCopyPrompt2: () -> Unit,
    onCopyPrompt3: () -> Unit,
    onCopyPromptBundle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (wideMode) 8.dp else 12.dp)) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(if (wideMode) 12.dp else 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Clipboard manager",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt1,
                    onValueChange = onPrompt1Change,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt 1") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt2,
                    onValueChange = onPrompt2Change,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt 2") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt3,
                    onValueChange = onPrompt3Change,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt 3") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        enabled = controlsEnabled,
                        onClick = onCopyPrompt1,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("P1", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(
                        enabled = controlsEnabled,
                        onClick = onCopyPrompt2,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("P2", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(
                        enabled = controlsEnabled,
                        onClick = onCopyPrompt3,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("P3", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(
                        enabled = controlsEnabled,
                        onClick = onCopyPromptBundle,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Nạp 1-2-3", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = controlsEnabled,
                onClick = onPickPdf,
                contentPadding = PaddingValues(if (wideMode) 10.dp else 14.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (wideMode) "PDF" else "Chọn PDF")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = controlsEnabled,
                onClick = onPickImages,
                contentPadding = PaddingValues(if (wideMode) 10.dp else 14.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (wideMode) "Ảnh" else "Chọn ảnh")
            }
        }

    }
}

@Composable
private fun StatusCard(
    status: String,
    wideMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
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
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = if (wideMode) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PdfPageRow(
    pdfUri: Uri,
    pageIndex: Int,
    enabled: Boolean,
    wideMode: Boolean,
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
                .padding(if (wideMode) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ThumbnailBox(size = if (wideMode) 48.dp else 60.dp) {
                    if (thumbnailFile != null) {
                        AsyncImage(
                            model = thumbnailFile,
                            contentDescription = "Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Trang ${pageIndex + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (wideMode) {
                IconButton(enabled = enabled, onClick = onShare) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Share")
                }
            } else {
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
}

@Composable
private fun ImageRow(
    uri: Uri,
    imageIndex: Int,
    enabled: Boolean,
    wideMode: Boolean,
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
                .padding(if (wideMode) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ThumbnailBox(size = if (wideMode) 48.dp else 60.dp) {
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
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ảnh ${imageIndex + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (wideMode) {
                IconButton(enabled = enabled, onClick = onShare) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Share")
                }
            } else {
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
}

@Composable
private fun ThumbnailBox(size: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
        content = content
    )
}
