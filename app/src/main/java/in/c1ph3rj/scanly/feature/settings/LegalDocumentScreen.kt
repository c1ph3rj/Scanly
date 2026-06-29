package `in`.c1ph3rj.scanly.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

enum class LegalDocumentType(
    val url: String,
    val title: String,
) {
    Privacy(
        url = "https://scanly.c1ph3rj.in/privacy-policy",
        title = "Privacy Policy",
    ),
    Terms(
        url = "https://scanly.c1ph3rj.in/terms-and-conditions",
        title = "Terms & Conditions",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentRoute(
    documentType: LegalDocumentType,
    onNavigateUp: () -> Unit,
) {
    LegalDocumentScreen(
        documentType = documentType,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    documentType: LegalDocumentType,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkTheme = backgroundColor.luminance() < 0.5f
    var loadProgress by remember(documentType) { mutableIntStateOf(0) }
    var loadFailed by remember(documentType) { mutableStateOf(false) }

    val webView = remember(context, documentType) {
        WebView(context).apply {
            setBackgroundColor(backgroundColor.toArgb())
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = true

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loadFailed = false
                    loadProgress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadProgress = 100
                    view?.applyReadOnlyPageConfiguration(isDarkTheme)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        loadFailed = true
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true &&
                        (errorResponse?.statusCode ?: 0) >= 400
                    ) {
                        loadFailed = true
                    }
                }
            }
            loadUrl(documentType.url)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(text = documentType.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    view.setBackgroundColor(backgroundColor.toArgb())
                    if (loadProgress == 100) {
                        view.applyReadOnlyPageConfiguration(isDarkTheme)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (!loadFailed && loadProgress < 100) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            if (loadFailed) {
                LegalDocumentLoadError(
                    documentTitle = documentType.title,
                    onRetry = {
                        loadFailed = false
                        loadProgress = 0
                        webView.loadUrl(documentType.url)
                    },
                    onOpenInBrowser = {
                        context.openExternalUri(Uri.parse(documentType.url))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentLoadError(
    documentTitle: String,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$documentTitle couldn't be loaded",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Check your internet connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("Retry")
        }
        TextButton(onClick = onOpenInBrowser) {
            Text("Open in browser")
        }
    }
}

private fun Context.openExternalUri(uri: Uri) {
    val supportedScheme = uri.scheme?.lowercase() in setOf("http", "https", "mailto")
    if (!supportedScheme) return

    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // The WebView keeps the current legal page when no handler is installed.
    }
}

private fun WebView.applyReadOnlyPageConfiguration(isDarkTheme: Boolean) {
    val theme = if (isDarkTheme) "dark" else "light"
    evaluateJavascript(
        """
        (() => {
          try {
            window.localStorage.setItem('scanly-theme', '$theme');
            document.documentElement.dataset.theme = '$theme';

            if (!document.getElementById('scanly-read-only-style')) {
              const style = document.createElement('style');
              style.id = 'scanly-read-only-style';
              style.textContent = `
                a, button, input, select, textarea, label, summary,
                [role='button'], [role='link'], [onclick] {
                  pointer-events: none !important;
                  cursor: default !important;
                }
              `;
              document.head.appendChild(style);
            }

            if (!window.__scanlyReadOnlyListenersInstalled) {
              const blockInteraction = (event) => {
                event.preventDefault();
                event.stopImmediatePropagation();
              };
              ['click', 'auxclick', 'submit', 'contextmenu', 'dragstart'].forEach((type) => {
                document.addEventListener(type, blockInteraction, true);
              });
              document.addEventListener('keydown', (event) => {
                const interactiveTarget = event.target?.closest?.(
                  "a, button, input, select, textarea, label, summary, [role='button'], [role='link'], [onclick]"
                );
                if (interactiveTarget && (event.key === 'Enter' || event.key === ' ')) {
                  blockInteraction(event);
                }
              }, true);
              window.__scanlyReadOnlyListenersInstalled = true;
            }
          } catch (_) {}
        })();
        """.trimIndent(),
        null,
    )
}
