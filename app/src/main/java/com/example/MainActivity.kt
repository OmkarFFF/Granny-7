package com.example

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.audio.HorrorAudioEngine
import com.example.engine.GrannyRenderer
import com.example.game.FarmhouseWorld
import com.example.game.GameSettings
import com.example.game.GameState
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.engine.MeshFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: HorrorAudioEngine
    private lateinit var world: FarmhouseWorld
    private lateinit var renderer: GrannyRenderer
    private var glSurfaceView: GLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        // Asynchronously preload meshes in background thread
        CoroutineScope(Dispatchers.Default).launch {
            MeshFactory.preload()
        }

        audioEngine = HorrorAudioEngine(this)
        world = FarmhouseWorld()

        setContent {
            MyApplicationTheme {
                var gameState by remember { mutableStateOf(GameState.MAIN_MENU) }
                var currentSettings by remember { mutableStateOf(GameSettings()) }
                var isScareActive by remember { mutableStateOf(false) }
                var notificationText by remember { mutableStateOf("") }

                // Auto-clear notification after 3.5s
                LaunchedEffect(notificationText) {
                    if (notificationText.isNotEmpty()) {
                        delay(3500)
                        notificationText = ""
                    }
                }

                // Instantiate Renderer once
                val gameRenderer = remember {
                    GrannyRenderer(
                        context = this@MainActivity,
                        world = world,
                        audio = audioEngine,
                        onStateChange = { newState ->
                            gameState = newState
                        },
                        onScareChange = { scare ->
                            isScareActive = scare
                        },
                        onNotification = { msg ->
                            notificationText = msg
                        }
                    ).also {
                        renderer = it
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. OpenGL ES 2.0 3D Game View
                    AndroidView(
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(2)
                                setPreserveEGLContextOnPause(true)
                                setRenderer(gameRenderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                glSurfaceView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. State-driven Compose UI Overlays
                    when (gameState) {
                        GameState.MAIN_MENU -> {
                            MainMenuOverlay(
                                onPlay = {
                                    gameState = GameState.OPTIONS
                                    gameRenderer.currentGameState = GameState.OPTIONS
                                },
                                onQuitGame = {
                                    finish()
                                },
                                audioEngine = audioEngine
                            )
                        }

                        GameState.OPTIONS -> {
                            OptionsOverlay(
                                initialSettings = currentSettings,
                                onSaveAndContinue = { newSettings ->
                                    currentSettings = newSettings
                                    gameRenderer.applySettings(newSettings)
                                    gameState = GameState.TIPS_STORY
                                    gameRenderer.currentGameState = GameState.TIPS_STORY
                                },
                                onBack = {
                                    gameState = GameState.MAIN_MENU
                                    gameRenderer.currentGameState = GameState.MAIN_MENU
                                },
                                audioEngine = audioEngine
                            )
                        }

                        GameState.TIPS_STORY -> {
                            TipsStoryOverlay(
                                onStartGame = {
                                    gameRenderer.applySettings(currentSettings)
                                    gameRenderer.startDay(1)
                                    gameState = GameState.GAMEPLAY
                                    gameRenderer.currentGameState = GameState.GAMEPLAY
                                    if (currentSettings.music) {
                                        audioEngine.startAmbience()
                                    }
                                },
                                onBackToOptions = {
                                    gameState = GameState.OPTIONS
                                    gameRenderer.currentGameState = GameState.OPTIONS
                                },
                                audioEngine = audioEngine
                            )
                        }

                        GameState.GAMEPLAY, GameState.SLEDRINA_SCARE -> {
                            GrannyGameHUD(
                                renderer = gameRenderer,
                                gameState = gameState,
                                notificationText = notificationText,
                                onStateChange = { newState ->
                                    gameState = newState
                                    gameRenderer.currentGameState = newState
                                }
                            )

                            // If Sledrina scare event is active
                            if (isScareActive) {
                                SledrinaScareOverlay()
                            }
                        }

                        GameState.PAUSED -> {
                            PauseDialog(
                                onResume = {
                                    gameState = GameState.GAMEPLAY
                                    gameRenderer.currentGameState = GameState.GAMEPLAY
                                },
                                onRestart = {
                                    gameRenderer.startDay(gameRenderer.playerDays)
                                    gameState = GameState.GAMEPLAY
                                    gameRenderer.currentGameState = GameState.GAMEPLAY
                                },
                                onMainMenu = {
                                    gameState = GameState.MAIN_MENU
                                    gameRenderer.currentGameState = GameState.MAIN_MENU
                                    audioEngine.stopAmbience()
                                }
                            )
                        }

                        GameState.GAME_OVER -> {
                            GameOverOverlay(
                                onRetry = {
                                    gameRenderer.startDay(1)
                                    gameState = GameState.GAMEPLAY
                                    gameRenderer.currentGameState = GameState.GAMEPLAY
                                },
                                onMainMenu = {
                                    gameRenderer.startDay(1)
                                    gameState = GameState.MAIN_MENU
                                    gameRenderer.currentGameState = GameState.MAIN_MENU
                                    audioEngine.stopAmbience()
                                }
                            )
                        }

                        GameState.WIN -> {
                            WinOverlay(
                                onPlayAgain = {
                                    gameRenderer.startDay(1)
                                    gameState = GameState.MAIN_MENU
                                    gameRenderer.currentGameState = GameState.MAIN_MENU
                                    audioEngine.stopAmbience()
                                }
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun hideSystemUI() {
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
        if (::audioEngine.isInitialized) {
            audioEngine.startAmbience()
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        if (::audioEngine.isInitialized) {
            audioEngine.stopAmbience()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::audioEngine.isInitialized) {
            audioEngine.release()
        }
    }
}
