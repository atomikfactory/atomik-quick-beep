package com.atomik.quickbeep

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.atomik.quickbeep.data.database.AppDatabase
import com.atomik.quickbeep.data.repository.CardRepositoryImpl
import com.atomik.quickbeep.security.BiometricPromptManager
import com.atomik.quickbeep.ui.screens.vault.VaultScreen
import com.atomik.quickbeep.ui.theme.AtomikQuickBeepTheme
import com.atomik.quickbeep.ui.viewmodel.CardsViewModel

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: CardsViewModel
    private lateinit var biometricPromptManager: BiometricPromptManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize room DB & repo
        val database = AppDatabase.getDatabase(this)
        val repository = CardRepositoryImpl(database.cardDao())
        
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        viewModel = ViewModelProvider(this, CardsViewModel.Factory(repository, sharedPrefs))[CardsViewModel::class.java]
        biometricPromptManager = BiometricPromptManager(this)

        // Trigger cold boot biometric prompt
        authenticateUser()

        setContent {
            AtomikQuickBeepTheme {
                val isUnlocked by viewModel.isUnlocked.collectAsState()

                if (isUnlocked) {
                    // Authenticated Vault Main Screen
                    VaultScreen(viewModel = viewModel)
                } else {
                    // Lock Screen UI
                    LockScreen(
                        onUnlockClicked = { authenticateUser() }
                    )
                }
            }
        }
    }

    private fun authenticateUser() {
        if (!biometricPromptManager.canAuthenticate()) {
            // If biometrics not supported or setup, bypass lock to avoid bricking app
            viewModel.setUnlocked(true)
            return
        }

        biometricPromptManager.showBiometricPrompt(
            title = "Atomik Quick Beep Lock",
            description = "Authenticate to unlock your secure loyalty card vault"
        ) { result ->
            when (result) {
                is BiometricPromptManager.BiometricResult.Success -> {
                    viewModel.setUnlocked(true)
                }
                is BiometricPromptManager.BiometricResult.Error -> {
                    Toast.makeText(this, "Authentication error: ${result.errorString}", Toast.LENGTH_SHORT).show()
                }
                is BiometricPromptManager.BiometricResult.Failed -> {
                    Toast.makeText(this, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun LockScreen(onUnlockClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "App Locked Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Atomik Quick Beep",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Your digital loyalty card vault is locked.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = onUnlockClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = "Unlock Vault", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}
