package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DomainConstants
import com.example.data.model.PostDoc
import com.example.ui.theme.PortalTheme
import com.example.ui.viewmodel.ActiveDialog
import com.example.ui.viewmodel.PortalUiState
import com.example.util.DateUtils
import com.example.util.ImageStorageHelper

@Composable
fun NewsScreen(
    state: PortalUiState,
    onSelectCategory: (String) -> Unit,
    onTogglePin: (PostDoc) -> Unit,
    onOpenDialog: (ActiveDialog) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val categoryFilter = state.filters.newsCategory
    val filteredPosts = remember(state.posts, categoryFilter, state.session) {
        val userEmail = state.session?.email?.trim()?.lowercase().orEmpty()
        state.posts.filter { post ->
            val matchesCat = categoryFilter.isBlank() || post.category == categoryFilter
            val matchesScope = state.isAdmin ||
                post.scope == "Toàn thôn" ||
                (userEmail.isNotBlank() && userEmail in post.allowedEmails) ||
                (state.session != null && state.session.cluster.isNotBlank() && post.cluster == state.session.cluster)
            matchesCat && matchesScope
        }.sortedWith(
            compareByDescending<PostDoc> { it.pinned }
                .thenByDescending { it.dateIso }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Bảng tin Thôn Long An",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Thông tin chính thức và nội dung tuyên truyền đồng bộ trực tuyến.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = categoryFilter.isBlank(),
                    onClick = { onSelectCategory("") },
                    label = { Text("Tất cả chuyên mục") },
                    modifier = Modifier.testTag("news_filter_all"),
                )
                DomainConstants.POST_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { onSelectCategory(if (categoryFilter == cat) "" else cat) },
                        label = { Text(cat) },
                    )
                }
            }
        }

        if (filteredPosts.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Campaign,
                    title = "Chưa có thông báo phù hợp",
                    subtitle = if (state.isAdmin) {
                        "Hãy chọn chuyên mục khác hoặc nhấn Đăng tin mới để thông báo đến bà con."
                    } else {
                        "Hiện chưa có thông báo nào trong chuyên mục này."
                    },
                )
            }
        } else {
            items(filteredPosts, key = { it.id }) { post ->
                NewsPostCard(
                    post = post,
                    isAdmin = state.isAdmin,
                    onCopyZalo = {
                        val zaloText = buildZaloFormattedText(post)
                        copyToClipboard(context, zaloText)
                        onNotify("Đã sao chép nội dung để gửi Zalo.")
                    },
                    onShare = {
                        val zaloText = buildZaloFormattedText(post)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, post.title)
                            putExtra(Intent.EXTRA_TEXT, zaloText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Chia sẻ thông báo"))
                    },
                    onTogglePin = { onTogglePin(post) },
                    onEdit = { onOpenDialog(ActiveDialog.PostEditor(post)) },
                    onDelete = { onOpenDialog(ActiveDialog.ConfirmDeletePost(post)) },
                )
            }
        }
    }
}

@Composable
private fun NewsPostCard(
    post: PostDoc,
    isAdmin: Boolean,
    onCopyZalo: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val decodedBitmap = remember(post.imageData) {
        ImageStorageHelper.decodeBase64Bitmap(post.imageData)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = "Ảnh minh họa: ${post.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }

            val semColors = PortalTheme.colors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = post.category,
                    backgroundColor = semColors.successBg,
                    textColor = semColors.successText,
                )
                if (post.pinned) {
                    StatusPill(
                        text = "★ Đã ghim",
                        backgroundColor = semColors.amberBg,
                        textColor = semColors.amberText,
                    )
                }
            }

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (post.scope == "Toàn thôn") "Phạm vi: Toàn thôn" else "Phạm vi: ${post.cluster}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = DateUtils.formatDateForDisplay(post.dateIso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCopyZalo,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sao chép gửi Zalo")
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chia sẻ")
                }

                if (isAdmin) {
                    TextButton(
                        onClick = onTogglePin,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = if (post.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (post.pinned) "Bỏ ghim" else "Ghim tin")
                    }
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sửa")
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Xóa", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun buildZaloFormattedText(post: PostDoc): String {
    val scopeLabel = if (post.scope == "Toàn thôn") "Toàn thôn" else post.cluster
    return "📢 ${post.title.uppercase()}\n" +
        "📌 Chuyên mục: ${post.category}\n" +
        "📍 Phạm vi: $scopeLabel\n\n" +
        "${post.content}\n\n" +
        "Trân trọng thông báo!"
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Thông báo Thôn Long An", text))
}
