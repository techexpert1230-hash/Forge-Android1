package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.webkit.WebViewAssetLoader
import com.example.ai.AiCoachRepository
import com.example.auth.signOut
import com.example.data.repository.FitnessRepository
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.notifications.ReminderChannels
import com.example.notifications.ReminderScheduler
import com.example.ui.theme.ForgeBackground
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ForgeWebViewScreen(
    currentUserId: String,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val healthConnectManager = remember(context) { HealthConnectManager(context) }
    val reminderScheduler = remember(context) { ReminderScheduler(context) }

    val viewModel: ForgeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
                val user = Firebase.auth.currentUser
                ForgeViewModel(
                    repository = FitnessRepository(app),
                    aiCoach = AiCoachRepository(),
                    healthConnect = HealthConnectManager(app),
                    reminderScheduler = ReminderScheduler(app),
                    currentUserId = currentUserId,
                    userEmail = user?.email,
                    displayName = user?.displayName
                )
            }
        }
    )

    val stateJson by viewModel.stateJsonFlow.collectAsStateWithLifecycle()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webReady by remember { mutableStateOf(false) }

    val statusTopDp = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value }
    val navBottomDp = with(density) {
        maxOf(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value,
            WindowInsets.ime.asPaddingValues().calculateBottomPadding().value
        )
    }

    val healthPermLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.refreshHealthConnectAndSync()
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.sendTestReminderNotification()
            webViewRef?.evaluateJavascript("window.__FORGE_TOAST && window.__FORGE_TOAST('Notifications enabled');", null)
        } else {
            webViewRef?.evaluateJavascript("window.__FORGE_TOAST && window.__FORGE_TOAST('Notification permission denied');", null)
        }
    }

    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null) {
            wv.evaluateJavascript("(function(){ return window.__FORGE_ON_BACK ? window.__FORGE_ON_BACK() : false; })()") { result ->
                if (result != "true") {
                    (context as? android.app.Activity)?.moveTaskToBack(true)
                }
            }
        }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    val bridge = remember(viewModel, healthConnectManager) {
        object {
            @JavascriptInterface
            fun saveProfile(json: String) = viewModel.saveProfile(json)

            @JavascriptInterface
            fun saveWorkout(json: String) = viewModel.saveWorkout(json)

            @JavascriptInterface
            fun deleteWorkout(id: String) = viewModel.deleteWorkout(id)

            @JavascriptInterface
            fun saveDailyLog(json: String) = viewModel.saveDailyLog(json)

            @JavascriptInterface
            fun saveGoal(json: String) = viewModel.saveGoal(json)

            @JavascriptInterface
            fun deleteGoal(id: String) = viewModel.deleteGoal(id)

            @JavascriptInterface
            fun saveMeasurement(json: String) = viewModel.saveMeasurement(json)

            @JavascriptInterface
            fun deleteMeasurement(dateKey: String) = viewModel.deleteMeasurement(dateKey)

            @JavascriptInterface
            fun saveRoutine(json: String) = viewModel.saveRoutine(json)

            @JavascriptInterface
            fun deleteRoutine(id: String) = viewModel.deleteRoutine(id)

            @JavascriptInterface
            fun saveCustomExercise(json: String) = viewModel.saveCustomExercise(json)

            @JavascriptInterface
            fun saveCustomMeal(json: String) = viewModel.saveCustomMeal(json)

            @JavascriptInterface
            fun eraseAllData() = viewModel.eraseAllData()

            @JavascriptInterface
            fun askAiCoach(requestId: String, message: String, contextJson: String) =
                viewModel.askAiCoach(requestId, message, contextJson)

            @JavascriptInterface
            fun generateAiWorkout(
                requestId: String,
                goalFocus: String,
                equipment: String,
                availableIdsJson: String,
                contextJson: String
            ) = viewModel.generateAiWorkout(requestId, goalFocus, equipment, availableIdsJson, contextJson)

            @JavascriptInterface
            fun suggestAiDiet(requestId: String, preference: String, contextJson: String) =
                viewModel.suggestAiDiet(requestId, preference, contextJson)

            @JavascriptInterface
            fun analyzeAiProgress(requestId: String, contextJson: String) =
                viewModel.analyzeAiProgress(requestId, contextJson)

            @JavascriptInterface
            fun requestHealthConnect() {
                webViewRef?.post {
                    when (healthConnectManager.getAvailability()) {
                        HealthConnectAvailability.AVAILABLE -> {
                            healthPermLauncher.launch(healthConnectManager.requiredPermissions)
                        }
                        HealthConnectAvailability.UPDATE_REQUIRED -> {
                            val uriString =
                                "market://details?id=${HealthConnectManager.PROVIDER_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding"
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setPackage("com.android.vending")
                                        data = Uri.parse(uriString)
                                    }
                                )
                            }
                        }
                        HealthConnectAvailability.UNAVAILABLE -> {
                            webViewRef?.evaluateJavascript(
                                "window.__FORGE_TOAST && window.__FORGE_TOAST('Health Connect is not available on this device');",
                                null
                            )
                        }
                    }
                }
            }

            @JavascriptInterface
            fun syncHealthConnectNow() {
                viewModel.syncStepsFromHealthConnect(silent = false)
            }

            @JavascriptInterface
            fun requestNotificationPermission() {
                webViewRef?.post {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ReminderChannels.hasNotificationPermission(context)
                    ) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.sendTestReminderNotification()
                        webViewRef?.evaluateJavascript(
                            "window.__FORGE_TOAST && window.__FORGE_TOAST('Notification test sent');",
                            null
                        )
                    }
                }
            }

            @JavascriptInterface
            fun copyToClipboard(text: String) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Forge Backup", text))
            }

            @JavascriptInterface
            fun signOutGoogle() {
                webViewRef?.post {
                    signOut(
                        credentialManager = credentialManager,
                        scope = coroutineScope,
                        onSignOutComplete = onSignedOut
                    )
                }
            }
        }
    }

    LaunchedEffect(webReady, stateJson) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (webReady && stateJson.isNotEmpty()) {
            val quoted = JSONObject.quote(stateJson)
            wv.evaluateJavascript("window.__FORGE_ON_STATE && window.__FORGE_ON_STATE($quoted);", null)
        }
    }

    LaunchedEffect(webReady, statusTopDp, navBottomDp) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (webReady) {
            wv.evaluateJavascript(
                "window.__FORGE_SET_INSETS && window.__FORGE_SET_INSETS($statusTopDp, $navBottomDp);",
                null
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { ev ->
            val wv = webViewRef ?: return@collectLatest
            when (ev) {
                is WebEvent.Toast -> {
                    val quoted = JSONObject.quote(ev.message)
                    wv.evaluateJavascript("window.__FORGE_TOAST && window.__FORGE_TOAST($quoted);", null)
                }
                is WebEvent.AiResult -> {
                    val kindQuoted = JSONObject.quote(ev.kind)
                    val payloadQuoted = JSONObject.quote(ev.payloadJson)
                    wv.evaluateJavascript(
                        "window.__FORGE_ON_AI_RESULT && window.__FORGE_ON_AI_RESULT($kindQuoted, $payloadQuoted);",
                        null
                    )
                }
                is WebEvent.SyncStatus -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ForgeBackground)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#0A0B0D"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                    }
                    addJavascriptInterface(bridge, "ForgeBridge")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            webReady = true
                            val latest = stateJson
                            if (latest.isNotEmpty()) {
                                val quoted = JSONObject.quote(latest)
                                view?.evaluateJavascript("window.__FORGE_ON_STATE && window.__FORGE_ON_STATE($quoted);", null)
                            }
                            view?.evaluateJavascript(
                                "window.__FORGE_SET_INSETS && window.__FORGE_SET_INSETS($statusTopDp, $navBottomDp);",
                                null
                            )
                        }
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/forge/index.html")
                    webViewRef = this
                }
            }
        )
    }
}
