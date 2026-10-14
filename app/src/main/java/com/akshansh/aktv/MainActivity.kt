package com.akshansh.aktv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AktvTheme {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    HomeScreen(vm)
                    if (vm.playIndex >= 0) {
                        BackHandler { vm.stop() }
                        PlayerScreen(vm)
                    }
                }
            }
        }
    }

    // MENU key on the remote = refresh playlist
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && vm.playIndex < 0) {
            vm.refresh()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
