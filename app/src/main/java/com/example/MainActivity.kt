package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.SignInScreen
import com.example.firebase.configureAppCheck
import com.example.ui.ForgeWebViewScreen
import com.example.ui.theme.ForgeBackground
import com.example.ui.theme.ForgeTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure App Check with launch intent before UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        // Step 2: Enable Edge-to-Edge with Neon Dark system bar styling
        val darkScrim = ForgeBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(darkScrim),
            navigationBarStyle = SystemBarStyle.dark(darkScrim)
        )

        setContent {
            ForgeTheme {
                ForgeAppRoot()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun ForgeAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = { }
        )
    } else {
        key(user.uid) {
            ForgeWebViewScreen(
                currentUserId = user.uid,
                onSignedOut = { }
            )
        }
    }
}
