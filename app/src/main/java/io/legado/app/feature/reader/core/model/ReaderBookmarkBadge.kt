package io.legado.app.feature.reader.core.model

import androidx.compose.runtime.Stable

/** Per-page decoration, so page transforms and curl clipping also apply to the bookmark. */
@Stable
data class ReaderBookmarkBadge(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Int,
    val heightPx: Int,
    val imageSource: String = "",
    val imageVersion: String = "",
) {
    companion object {
        fun create(
            hasBookmark: Boolean,
            isScroll: Boolean,
            pageWidthPx: Int,
            headerBottomPx: Float,
            contentRightPaddingPx: Int,
            density: Float,
            sizeDp: Int,
            imageSource: String = "",
            imageVersion: String = "",
        ): ReaderBookmarkBadge? {
            // A zero-size badge explicitly hides the decoration while preserving the bookmark.
            if (!hasBookmark || isScroll || sizeDp <= 0) return null
            val width = (sizeDp * density).toInt().coerceAtLeast(1)
            return ReaderBookmarkBadge(
                leftPx = pageWidthPx - contentRightPaddingPx - 6 * density - width,
                // beta.23 之前的旧 PageView 行为：角标贴页眉下沿（content_text_view 顶边），
                // 正文被正文中段上边距下推，不会被角标挡住。
                topPx = headerBottomPx + 2 * density,
                widthPx = width,
                heightPx = width * 2,
                imageSource = imageSource,
                imageVersion = imageVersion,
            )
        }
    }
}
