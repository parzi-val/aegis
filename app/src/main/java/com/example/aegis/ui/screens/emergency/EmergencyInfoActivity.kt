package com.example.aegis.ui.screens.emergency

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.aegis.ui.theme.AegisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmergencyInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen without requiring unlock.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            AegisTheme {
                val vm: EmergencyInfoViewModel = hiltViewModel()
                val patient    by vm.patient.collectAsStateWithLifecycle()
                val allergies  by vm.allergies.collectAsStateWithLifecycle()
                val conditions by vm.conditions.collectAsStateWithLifecycle()
                val medications by vm.medications.collectAsStateWithLifecycle()

                EmergencyInfoScreen(
                    patient    = patient,
                    allergies  = allergies,
                    conditions = conditions,
                    medications = medications,
                    onClose    = { finish() },
                )
            }
        }
    }
}
