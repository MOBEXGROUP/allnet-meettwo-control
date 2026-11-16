package org.roomscreen.meettwo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.roomscreen.meettwo.container
import org.roomscreen.meettwo.service.BridgeService
import org.roomscreen.meettwo.ui.theme.MeetTwoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BridgeService.start(this)
        val factory = viewModelFactory { initializer { MainViewModel(container) } }
        setContent {
            MeetTwoTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                if (settingsOpen) {
                    SettingsScreen(viewModel, back = { settingsOpen = false })
                } else {
                    MainScreen(viewModel, openSettings = { settingsOpen = true })
                }
            }
        }
    }
}
