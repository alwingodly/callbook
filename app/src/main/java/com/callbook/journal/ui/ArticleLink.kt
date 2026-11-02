package com.callbook.journal.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.callbook.journal.data.PublicQuotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the publisher page. A Google News wrapper is turned into the real article first. */
fun CoroutineScope.openArticle(context: Context, link: String, onBusy: (Boolean) -> Unit) {
    if (link.isBlank()) return
    launch {
        onBusy(true)
        val url = withContext(Dispatchers.IO) {
            runCatching { PublicQuotes.articleUrl(link) }.getOrDefault(link)
        }
        if (url.startsWith("http")) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            runCatching { context.startActivity(view) }
        }
        onBusy(false)
    }
}
