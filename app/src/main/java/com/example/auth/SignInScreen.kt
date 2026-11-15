package com.example.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.ForgeBackground
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeError
import com.example.ui.theme.ForgeNeonLime
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeSurface2
import com.example.ui.theme.ForgeTextMuted
import com.example.ui.theme.ForgeTextPrimary
import com.example.ui.theme.ForgeTextSecondary

@Composable
fun SignInScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isAttemptingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAttemptingSilent = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAttemptingSilent = false
            },
            scope = coroutineScope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ForgeNeonLime.copy(alpha = 0.14f),
                        ForgeBackground,
                        ForgeBackground
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ForgeNeonLime,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Forge Logo",
                            tint = ForgeBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = "FORGE",
                    style = MaterialTheme.typography.titleLarge,
                    color = ForgeTextPrimary,
                    fontWeight = FontWeight.Black
                )
            }

            Image(
                painter = painterResource(id = R.drawable.img_forge_hero),
                contentDescription = "Forge Neon Dark Fitness Hero",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Train with intent.\nTrack every rep.",
                    style = MaterialTheme.typography.displaySmall,
                    color = ForgeTextPrimary
                )
                Text(
                    text = "Offline-first workouts, Health Connect sync, complete lifetime analytics, and free AI coaching synced to your account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ForgeTextSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureHighlightRow(
                    icon = Icons.Rounded.FitnessCenter,
                    title = "Complete Fitness & History",
                    subtitle = "Workouts, push-ups, squats, meals, water & calendar edits"
                )
                FeatureHighlightRow(
                    icon = Icons.Rounded.CloudDone,
                    title = "Offline Cache + Cloud Sync",
                    subtitle = "Never lose history when switching devices or offline"
                )
                FeatureHighlightRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Free AI Coach & Health Connect",
                    subtitle = "Gemini workout & diet plans plus Android 16 step sync"
                )
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForgeSurface2),
                    border = BorderStroke(1.dp, ForgeError.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = ForgeError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = coroutineScope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading && !isAttemptingSilent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForgeNeonLime,
                    contentColor = ForgeBackground,
                    disabledContainerColor = ForgeNeonLime.copy(alpha = 0.5f),
                    disabledContentColor = ForgeBackground
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading || isAttemptingSilent) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = ForgeBackground,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isAttemptingSilent) "Checking account…" else "Signing in…",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Your workout logs, personal records, and daily history remain permanently protected under your Google account.",
                style = MaterialTheme.typography.bodyMedium,
                color = ForgeTextMuted
            )
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ForgeSurface),
        border = BorderStroke(1.dp, ForgeBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = ForgeSurface2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ForgeNeonLime,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ForgeTextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = ForgeTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
