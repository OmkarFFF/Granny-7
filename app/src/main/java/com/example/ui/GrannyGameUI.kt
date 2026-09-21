package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.HorrorAudioEngine
import com.example.engine.GrannyRenderer
import com.example.game.DifficultyLevel
import com.example.game.GameSettings
import com.example.game.GameState
import com.example.game.GraphicsQuality
import com.example.game.HidingType
import com.example.game.ItemType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun GrannyGameHUD(
    renderer: GrannyRenderer,
    gameState: GameState,
    notificationText: String,
    onStateChange: (GameState) -> Unit,
    modifier: Modifier = Modifier
) {
    var joyThumbX by remember { mutableFloatStateOf(0f) }
    var joyThumbY by remember { mutableFloatStateOf(0f) }

    // Rerender trigger state for HUD
    var hudTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(80)
            hudTick++
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // 1. Right-side touch area for Looking Around (Yaw / Pitch)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            renderer.lookDeltaX += dragAmount.x
                            renderer.lookDeltaY += dragAmount.y
                        }
                    )
                }
        )

        // 2. Left-side touch area for Virtual Thumbstick Movement
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.35f)
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, bottom = 48.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            // Joystick Outer Ring
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color(0x55111118))
                    .border(2.dp, Color(0x88992222), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                joyThumbX = 0f
                                joyThumbY = 0f
                                renderer.moveJoystickX = 0f
                                renderer.moveJoystickY = 0f
                            },
                            onDragCancel = {
                                joyThumbX = 0f
                                joyThumbY = 0f
                                renderer.moveJoystickX = 0f
                                renderer.moveJoystickY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                joyThumbX = (joyThumbX + dragAmount.x).coerceIn(-60f, 60f)
                                joyThumbY = (joyThumbY + dragAmount.y).coerceIn(-60f, 60f)
                                renderer.moveJoystickX = joyThumbX / 60f
                                renderer.moveJoystickY = -joyThumbY / 60f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Joystick Inner Thumb Knob
                Box(
                    modifier = Modifier
                        .offset { IntOffset(joyThumbX.roundToInt(), joyThumbY.roundToInt()) }
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xCCB71C1C))
                        .border(2.dp, Color(0xFFFF5252), CircleShape)
                )
            }
        }

        // 3. Top Status Bar: Day indicator, Threat Heartbeat, Pause Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day Counter
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xAA1A0505)),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF8B0000), Color(0xFF330000))))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = "Day",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DAY ${renderer.playerDays} / ${renderer.maxDays}",
                        color = Color(0xFFFFCDD2),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Proximity Threat / Heartbeat alert
            val grannyDist = renderer.playerPos.distanceTo(renderer.grannyAI.position)
            val isThreatNear = grannyDist < 12.0f
            if (isThreatNear) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC7F0000)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Threat",
                            tint = Color(0xFFFFF176),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (grannyDist < 5.0f) "SHE IS RIGHT HERE!" else "HEARING FOOTSTEPS...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Flashlight & Pause Controls
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Flashlight Toggle
                IconButton(
                    onClick = {
                        renderer.isFlashlightOn = !renderer.isFlashlightOn
                        renderer.audio.playDrawerOpen()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (renderer.isFlashlightOn) Color(0xAAFFD54F) else Color(0x88263238),
                            CircleShape
                        )
                        .testTag("flashlight_toggle")
                ) {
                    Icon(
                        imageVector = if (renderer.isFlashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = "Flashlight",
                        tint = if (renderer.isFlashlightOn) Color.Black else Color.White
                    )
                }

                // Pause Button
                IconButton(
                    onClick = { onStateChange(GameState.PAUSED) },
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0x88212121), CircleShape)
                        .testTag("pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        tint = Color.White
                    )
                }
            }
        }

        // 4. Notification / Interaction message banner
        AnimatedVisibility(
            visible = notificationText.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Surface(
                color = Color(0xDD0D0D12),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F))
            ) {
                Text(
                    text = notificationText,
                    color = Color(0xFFFFEBEE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 5. Hiding State Banner (if in wardrobe or under bed)
        if (renderer.playerHiding != HidingType.NONE) {
            Surface(
                color = Color(0xDD000000),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF66BB6A)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (renderer.playerHiding == HidingType.WARDROBE) "HIDING INSIDE WARDROBE" else "CRAWLING UNDER BED",
                        color = Color(0xFFA5D6A7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "You are safe from view. Tap Hand to emerge.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 6. Action Buttons on Right (Hand / Interact, Crouch, Drop Item)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Crouch / Stand Toggle
            IconButton(
                onClick = {
                    renderer.isCrouching = !renderer.isCrouching
                    renderer.audio.playFootstep(isCrouching = renderer.isCrouching)
                },
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        if (renderer.isCrouching) Color(0xCCE65100) else Color(0x992E3440),
                        CircleShape
                    )
                    .border(2.dp, Color(0xAAFFA726), CircleShape)
                    .testTag("crouch_button")
            ) {
                Icon(
                    imageVector = if (renderer.isCrouching) Icons.Filled.DirectionsRun else Icons.Filled.AirlineSeatReclineExtra,
                    contentDescription = "Crouch",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Drop Item Button
            if (renderer.inventory.isNotEmpty()) {
                IconButton(
                    onClick = {
                        renderer.dropCurrentItem()
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0x99424242), CircleShape)
                        .border(2.dp, Color(0xAA9E9E9E), CircleShape)
                        .testTag("drop_item_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RemoveCircleOutline,
                        contentDescription = "Drop Item",
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Primary Interaction Hand Button
            FilledIconButton(
                onClick = {
                    renderer.performInteraction()
                },
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, Color(0xFFFF5252), CircleShape)
                    .testTag("interact_hand_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xDD8B0000)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PanTool,
                    contentDescription = "Interact / Hand",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 7. Bottom Inventory Tray
        Surface(
            color = Color(0xEE121216),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x558B0000)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
        ) {
            if (renderer.inventory.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "INVENTORY EMPTY — SEARCH ROOMS & CHESTS",
                        color = Color(0xFF757575),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(renderer.inventory) { index, item ->
                        val isSelected = index == renderer.selectedItemIndex
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF371616) else Color(0xFF1E1E24),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Color(0xFFFF5252) else Color(0xFF424242)
                            ),
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { renderer.selectedItemIndex = index }
                                .padding(2.dp)
                                .testTag("inventory_item_$index")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val itemIcon = when (item.type) {
                                    ItemType.CAR_KEY, ItemType.GATE_KEY, ItemType.BEDROOM_KEY, ItemType.BOX_KEY -> Icons.Filled.VpnKey
                                    ItemType.BATTERY -> Icons.Filled.BatteryChargingFull
                                    ItemType.FUEL_CAN -> Icons.Filled.LocalGasStation
                                    ItemType.ENGINE_PART, ItemType.MECHANICAL_PART -> Icons.Filled.Build
                                    ItemType.BOLT_CUTTERS -> Icons.Filled.ContentCut
                                    ItemType.FLASHLIGHT -> Icons.Filled.FlashlightOn
                                }
                                Icon(
                                    imageVector = itemIcon,
                                    contentDescription = item.type.displayName,
                                    tint = if (isSelected) Color(0xFFFF8A80) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = item.type.displayName,
                                        color = if (isSelected) Color(0xFFFFEBEE) else Color(0xFFEEEEEE),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isSelected) "EQUIPPED" else "TAP TO EQUIP",
                                        color = if (isSelected) Color(0xFFFF5252) else Color(0xFF9E9E9E),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cinematic full-screen native Android Main Menu for Granny 7: The Famhouse.
 * Faithful to the visual reference poster with live 3D background, moonlight,
 * drifting volumetric fog, flickering lanterns, distressed typography, and interactive horror buttons.
 */
/**
 * Cinematic full-screen native Android Main Menu for Granny 7: The Famhouse.
 * Faithful to user specifications: EXACTLY three buttons (PLAY, MORE GAMES, QUIT).
 */
@Composable
fun MainMenuOverlay(
    onPlay: () -> Unit,
    onQuitGame: () -> Unit = {},
    audioEngine: HorrorAudioEngine? = null,
    modifier: Modifier = Modifier
) {
    var showQuitDialog by remember { mutableStateOf(false) }
    var showMoreGamesDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. High-Res Cinematic Matte Art
        Image(
            painter = painterResource(id = R.drawable.granny7_menu_poster),
            contentDescription = "Granny 7 The Famhouse Cinematic Poster",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.94f)
        )

        // 2. Cinematic Atmospheric Vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x22000000),
                            Color(0x66000000),
                            Color(0xBB000000)
                        ),
                        radius = 1200f
                    )
                )
        )

        // 3. Live Atmospheric Moonlight & Lantern Canvas
        MoonlightAtmosphereCanvas()

        // 4. Live Atmospheric Volumetric Drifting Ground Fog
        VolumetricFogCanvas()

        // 5. Live Drifting Dust/Embers
        HorrorParticlesCanvas()

        // 6. Interactive Native Android UI Layer
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            val isWideScreen = maxWidth > 680.dp

            // Top-Left: Distressed Horror Title & Subtitle
            GrannyTitleHeader(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp)
            )

            // Lower-Left: EXACTLY THREE MAIN BUTTONS: PLAY, MORE GAMES, QUIT
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // 1. PLAY Button -> Opens Options Screen
                HorrorMenuButton(
                    text = "PLAY",
                    isPrimary = true,
                    onClick = {
                        audioEngine?.playMenuClick()
                        onPlay()
                    },
                    testTag = "play_button"
                )

                // 2. MORE GAMES Button -> Shows dialog without crashing
                HorrorMenuButton(
                    text = "MORE GAMES",
                    isPrimary = false,
                    onClick = {
                        audioEngine?.playMenuClick()
                        showMoreGamesDialog = true
                    },
                    testTag = "more_games_button"
                )

                // 3. QUIT Button -> Opens confirmation dialog
                HorrorMenuButton(
                    text = "QUIT",
                    isPrimary = false,
                    onClick = {
                        audioEngine?.playMenuClick()
                        showQuitDialog = true
                    },
                    testTag = "quit_button"
                )
            }

            // Lower-Center: Distressed "GRANNY 7" Watermark
            if (isWideScreen) {
                WatermarkTitleEmblem(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            }
        }

        // More Games Dialog
        if (showMoreGamesDialog) {
            MoreGamesDialog(onDismiss = { showMoreGamesDialog = false })
        }

        // Quit Confirmation Dialog
        if (showQuitDialog) {
            QuitConfirmationDialog(
                onConfirmQuit = {
                    showQuitDialog = false
                    onQuitGame()
                },
                onDismiss = { showQuitDialog = false }
            )
        }
    }
}

/**
 * Functional dialog for MORE GAMES button.
 */
@Composable
fun MoreGamesDialog(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(Color(0xFF8B0000), Color(0xFF424242)))
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "MORE GAMES",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "No additional horror games installed on this device.\n\nUpcoming chapters:\n• Granny Chapter 8: The Asylum\n• Sledrina: The Curse\n• Grandpa's Hunting Lodge\n\nStay tuned for future releases!",
                    color = Color(0xFFCFD8DC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_more_games_button")
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Full Options Screen prior to starting gameplay.
 * Features Difficulty, Audio, Bots, Modifiers (Darker, Limping, Extra Locks), Quality, and Back/Continue.
 */
@Composable
fun OptionsOverlay(
    initialSettings: GameSettings,
    onSaveAndContinue: (GameSettings) -> Unit,
    onBack: () -> Unit,
    audioEngine: HorrorAudioEngine? = null,
    modifier: Modifier = Modifier
) {
    var difficulty by remember { mutableStateOf(initialSettings.difficulty) }
    var music by remember { mutableStateOf(initialSettings.music) }
    var soundEffects by remember { mutableStateOf(initialSettings.soundEffects) }
    var grannyEnabled by remember { mutableStateOf(initialSettings.grannyEnabled) }
    var grandpaEnabled by remember { mutableStateOf(initialSettings.grandpaEnabled) }
    var slendrinaEnabled by remember { mutableStateOf(initialSettings.slendrinaEnabled) }
    var extraLocks by remember { mutableStateOf(initialSettings.extraLocks) }
    var darker by remember { mutableStateOf(initialSettings.darker) }
    var limping by remember { mutableStateOf(initialSettings.limping) }
    var quality by remember { mutableStateOf(initialSettings.quality) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF208080D))
    ) {
        Image(
            painter = painterResource(id = R.drawable.granny7_menu_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.18f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "OPTIONS",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(60.dp)
                            .background(Color(0xFFFF1744))
                    )
                }
            }

            // Scrollable Settings Cards
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. DIFFICULTY
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x668B0000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DIFFICULTY",
                            color = Color(0xFFFF8A80),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(DifficultyLevel.values().size) { idx ->
                                val diff = DifficultyLevel.values()[idx]
                                val isSelected = difficulty == diff
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF8B0000) else Color(0xFF23232C),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) Color(0xFFFF5252) else Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            difficulty = diff
                                            audioEngine?.playMenuClick()
                                        }
                                        .testTag("difficulty_${diff.name.lowercase()}")
                                ) {
                                    Text(
                                        text = diff.displayName.uppercase(),
                                        color = if (isSelected) Color.White else Color(0xFFB0BEC5),
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = difficulty.description,
                            color = Color(0xFFCFD8DC),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 2. AUDIO TOGGLES
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33444444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "AUDIO",
                            color = Color(0xFFFF8A80),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        // Music Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("MUSIC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Atmospheric horror soundscape", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = music,
                                onCheckedChange = {
                                    music = it
                                    audioEngine?.isMusicEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_music")
                            )
                        }

                        Divider(color = Color(0x22FFFFFF))

                        // Sound Effects Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("SOUND EFFECTS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Footsteps, creaking doors, shotgun blasts", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = soundEffects,
                                onCheckedChange = {
                                    soundEffects = it
                                    audioEngine?.setSoundEnabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_sound_effects")
                            )
                        }
                    }
                }

                // 3. BOT TOGGLES
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33444444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "BOTS (CHARACTERS)",
                            color = Color(0xFFFF8A80),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        // Granny
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("GRANNY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Armed with a bloodstained club", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = grannyEnabled,
                                onCheckedChange = { grannyEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_granny")
                            )
                        }

                        Divider(color = Color(0x22FFFFFF))

                        // Grandpa
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("GRANDPA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Armed with a 12-gauge shotgun", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = grandpaEnabled,
                                onCheckedChange = { grandpaEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_grandpa")
                            )
                        }

                        Divider(color = Color(0x22FFFFFF))

                        // Slendrina
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("SLENDERINA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Materializes without warning in dark corridors", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = slendrinaEnabled,
                                onCheckedChange = { slendrinaEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_slendrina")
                            )
                        }
                    }
                }

                // 4. GAMEPLAY MODIFIERS
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33444444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "GAMEPLAY MODIFIERS",
                            color = Color(0xFFFF8A80),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        // Extra Locks
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("EXTRA LOCKS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Adds additional padlocks to escape exits", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = extraLocks,
                                onCheckedChange = { extraLocks = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_extra_locks")
                            )
                        }

                        Divider(color = Color(0x22FFFFFF))

                        // Darker Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DARKER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Farmhouse interior is steeped in deep darkness", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = darker,
                                onCheckedChange = { darker = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_darker")
                            )
                        }

                        Divider(color = Color(0x22FFFFFF))

                        // Limping Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("LIMPING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Player suffers an injury and walks with a limp", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = limping,
                                onCheckedChange = { limping = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF1744),
                                    checkedTrackColor = Color(0xFF5A0000)
                                ),
                                modifier = Modifier.testTag("switch_limping")
                            )
                        }
                    }
                }

                // 5. GRAPHICS QUALITY
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33444444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "QUALITY",
                            color = Color(0xFFFF8A80),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GraphicsQuality.values().forEach { q ->
                                val isSelected = quality == q
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF8B0000) else Color(0xFF23232C),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) Color(0xFFFF5252) else Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            quality = q
                                            audioEngine?.playMenuClick()
                                        }
                                        .testTag("quality_${q.name.lowercase()}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = q.displayName.uppercase(),
                                            color = if (isSelected) Color.White else Color(0xFFB0BEC5),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons: BACK & CONTINUE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        audioEngine?.playMenuClick()
                        onBack()
                    },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF757575)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("options_back_button")
                ) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BACK", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        audioEngine?.playMenuClick()
                        val updated = GameSettings(
                            difficulty = difficulty,
                            music = music,
                            soundEffects = soundEffects,
                            grannyEnabled = grannyEnabled,
                            grandpaEnabled = grandpaEnabled,
                            slendrinaEnabled = slendrinaEnabled,
                            extraLocks = extraLocks,
                            darker = darker,
                            limping = limping,
                            quality = quality
                        )
                        onSaveAndContinue(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("options_continue_button")
                ) {
                    Text("CONTINUE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

/**
 * Tips & Story Screen shown after Options.
 * Displays atmospheric lore and vital survival tips, with Back and Play Game buttons.
 */
@Composable
fun TipsStoryOverlay(
    onStartGame: () -> Unit,
    onBackToOptions: () -> Unit,
    audioEngine: HorrorAudioEngine? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF208080D))
    ) {
        Image(
            painter = painterResource(id = R.drawable.granny7_menu_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.22f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "THE FARMHOUSE",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(50.dp)
                            .background(Color(0xFFFF1744))
                    )
                }
            }

            // Scrollable Story and Tips
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Story Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x668B0000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "STORY",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "You awaken trapped inside an unfamiliar bedroom of a dark, decrepit farmhouse. The front door is reinforced with heavy iron padlocks and chains. Outside in the yard lies an abandoned vehicle with missing parts.\n\nGranny and Grandpa dwell here, listening for your every breath. You have only 4 days to gather the supplies and escape before you vanish forever.",
                            color = Color(0xFFECEFF1),
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }

                // Tips Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD181820)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33444444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "SURVIVAL TIPS",
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )

                        TipItem(
                            icon = Icons.Filled.Hearing,
                            title = "Granny Hears Everything",
                            desc = "Dropping items or running creates loud noises. Crouch and tread carefully to avoid alerting her."
                        )

                        Divider(color = Color(0x22FFFFFF))

                        TipItem(
                            icon = Icons.Filled.Adjust,
                            title = "Grandpa's 12-Gauge Shotgun",
                            desc = "Grandpa wanders the halls with his shotgun. If you step into his line of sight, take cover immediately!"
                        )

                        Divider(color = Color(0x22FFFFFF))

                        TipItem(
                            icon = Icons.Filled.Hotel,
                            title = "Hiding Under Beds & Wardrobes",
                            desc = "Interact with wardrobes or beds to conceal yourself. Stay still until the heavy footsteps fade away."
                        )

                        Divider(color = Color(0x22FFFFFF))

                        TipItem(
                            icon = Icons.Filled.VisibilityOff,
                            title = "Slendrina's Gaze",
                            desc = "When Slendrina materializes before you, immediately look away. Staring into her eyes causes mortal terror."
                        )

                        Divider(color = Color(0x22FFFFFF))

                        TipItem(
                            icon = Icons.Filled.DirectionsCar,
                            title = "Two Ways to Escape",
                            desc = "Repair the getaway car with a Battery, Fuel, Engine & Mechanical Parts, or unlock the South Gate with the Gate Key and Bolt Cutters."
                        )

                        Divider(color = Color(0x22FFFFFF))

                        TipItem(
                            icon = Icons.Filled.FlashlightOn,
                            title = "Flashlight Starts OFF",
                            desc = "Tap the flashlight icon in the HUD to toggle your electric torch when searching dark rooms."
                        )
                    }
                }
            }

            // Bottom Navigation Buttons: BACK & PLAY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        audioEngine?.playMenuClick()
                        onBackToOptions()
                    },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF757575)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tips_back_button")
                ) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BACK", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        audioEngine?.playMenuStartGame()
                        onStartGame()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tips_play_button")
                ) {
                    Text("PLAY GAME", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun TipItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0x338B0000),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = Color(0xFFB0BEC5), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/**
 * Top-left title and cursive subtitle matching the visual reference poster.
 */
@Composable
private fun GrannyTitleHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "GRANNY",
                color = Color.White,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                letterSpacing = 4.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xDD000000),
                        offset = Offset(4f, 6f),
                        blurRadius = 8f
                    )
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Iconic Blood-Red Slashed "7"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.offset(y = (-4).dp)
            ) {
                Text(
                    text = "7",
                    color = Color(0xFFFF1744),
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(0xFFFF0033),
                            offset = Offset(0f, 0f),
                            blurRadius = 24f
                        )
                    )
                )
                // Diagonal Blood Slash across the 7
                Canvas(
                    modifier = Modifier
                        .size(46.dp)
                        .offset(x = (-3).dp, y = 6.dp)
                ) {
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.75f)
                        lineTo(size.width * 0.45f, size.height * 0.40f)
                        lineTo(size.width * 0.95f, size.height * 0.10f)
                        lineTo(size.width, size.height * 0.30f)
                        lineTo(size.width * 0.40f, size.height * 0.95f)
                        close()
                    }
                    drawPath(path, color = Color(0xFFD50000))
                }
            }
        }

        // Subtitle: The Famhouse (cursive/italic styling)
        Text(
            text = "The Famhouse",
            color = Color(0xFFECEFF1),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Serif,
            letterSpacing = 2.sp,
            modifier = Modifier.offset(x = 6.dp, y = (-8).dp),
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0xEE000000),
                    offset = Offset(2f, 4f),
                    blurRadius = 6f
                )
            )
        )
    }
}

/**
 * Primary Horror Menu Button with distressed typography and glowing blood-red slash accent.
 */
@Composable
private fun HorrorMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    testTag: String = ""
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btn_scale"
    )

    Column(
        modifier = modifier
            .testTag(testTag)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable {
                isPressed = true
                onClick()
            },
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (isPrimary) 44.sp else 30.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif,
            letterSpacing = 4.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0xFFD50000),
                    offset = Offset(0f, 0f),
                    blurRadius = 14f
                )
            )
        )

        // Scratched Blood Slash Accent Underline
        Canvas(
            modifier = Modifier
                .width(if (isPrimary) 140.dp else 100.dp)
                .height(10.dp)
                .offset(y = (-4).dp)
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.7f)
                lineTo(w * 0.20f, h * 0.25f)
                lineTo(w * 0.60f, h * 0.55f)
                lineTo(w, h * 0.15f)
                lineTo(w * 0.95f, h * 0.85f)
                lineTo(w * 0.50f, h * 0.95f)
                close()
            }
            drawPath(path, color = Color(0xFFFF1744))
            drawCircle(
                color = Color(0xFFD50000),
                radius = h * 0.35f,
                center = Offset(w * 0.35f, h * 1.3f)
            )
        }
    }
}

/**
 * Secondary Horror Menu Action Button with subtle border and icon.
 */
@Composable
private fun HorrorSecondaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .testTag(testTag),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x99180A0C)
        ),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xAA8B0000)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color(0xFFFF5252),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = Color(0xFFECEFF1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Center-lower stylized distressed "GRANNY 7" watermark (matches the reference poster).
 */
@Composable
private fun WatermarkTitleEmblem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "GRANNY",
            color = Color(0xBBFFFFFF),
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif,
            letterSpacing = 4.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(2f, 3f),
                    blurRadius = 6f
                )
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "7",
            color = Color(0xEEFF1744),
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0xFFFF0033),
                    offset = Offset(0f, 0f),
                    blurRadius = 16f
                )
            )
        )
    }
}

/**
 * Atmospheric moonlight and warm lantern lighting simulation canvas.
 */
@Composable
private fun MoonlightAtmosphereCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "moon_lantern")
    val moonPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "moon_pulse"
    )
    val lanternFlicker by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lantern_flicker"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Moonlight upper-right glow
        val moonCenter = Offset(w * 0.88f, h * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x40E0F7FA),
                    Color(0x2080DEEA),
                    Color(0x0826C6DA),
                    Color.Transparent
                ),
                center = moonCenter,
                radius = (w * 0.24f) * moonPulse
            ),
            center = moonCenter,
            radius = (w * 0.24f) * moonPulse
        )

        // Porch/Stone Post lantern warm yellow light on left
        val lanternCenter = Offset(w * 0.26f, h * 0.50f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x45FFA000).copy(alpha = 0.35f * lanternFlicker),
                    Color(0x18FF6F00),
                    Color.Transparent
                ),
                center = lanternCenter,
                radius = w * 0.14f
            ),
            center = lanternCenter,
            radius = w * 0.14f
        )
    }
}

/**
 * Animated volumetric ground fog drifting across the farmhouse lawn.
 */
@Composable
private fun VolumetricFogCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "fog_flow")
    val fogPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fog1"
    )
    val fogPhase2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fog2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Ground fog layer 1
        val x1 = (fogPhase1 * w * 1.5f) % (w * 1.5f) - (w * 0.25f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x30546E7A), Color(0x1437474F), Color.Transparent),
                center = Offset(x1, h * 0.88f),
                radius = w * 0.45f
            ),
            topLeft = Offset(x1 - w * 0.45f, h * 0.68f),
            size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.40f)
        )

        // Ground fog layer 2
        val x2 = (fogPhase2 * w * 1.4f) % (w * 1.4f) - (w * 0.2f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x28607D8B), Color(0x10455A64), Color.Transparent),
                center = Offset(x2, h * 0.94f),
                radius = w * 0.50f
            ),
            topLeft = Offset(x2 - w * 0.50f, h * 0.74f),
            size = androidx.compose.ui.geometry.Size(w * 1.0f, h * 0.35f)
        )
    }
}

/**
 * Animated horror ambient particles/dust drifting in the air.
 */
@Composable
private fun HorrorParticlesCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles_time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val rad = Math.toRadians(time.toDouble())
        for (i in 0..16) {
            val px = ((i * 137.5f) % w) + (sin(rad + i).toFloat() * 18f)
            val py = ((i * 93.7f) % h) + (cos(rad * 0.8 + i).toFloat() * 14f)
            val pAlpha = (0.25f + sin(rad * 1.5 + i).toFloat() * 0.20f).coerceIn(0.05f, 0.55f)
            drawCircle(
                color = Color(0xFFB0BEC5).copy(alpha = pAlpha),
                radius = 2.2f + (i % 3),
                center = Offset(px, py)
            )
        }
    }
}

/**
 * Escape Objectives Dialog explaining the farmhouse escape routes.
 */
@Composable
private fun EscapeObjectivesDialog(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141318)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B0000)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ESCAPE OBJECTIVES",
                        color = Color(0xFFFF1744),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Serif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Divider(color = Color(0x448B0000), thickness = 1.dp)

                // Route 1: Car Escape
                ObjectiveCard(
                    title = "ROUTE 1: REPAIR THE ESCAPE CAR",
                    description = "Collect Battery, Fuel Can, Engine Part & Mechanical Part in the house. Place them in the car engine outside. Free Angelene to get the Car Key!",
                    color = Color(0xFFFF7043)
                )

                // Route 2: Main Gate Escape
                ObjectiveCard(
                    title = "ROUTE 2: UNLOCK MAIN GATE",
                    description = "Cut the heavy chains on the South Front Gate using Bolt Cutters, then unlock the master padlock using the Gate Key.",
                    color = Color(0xFFFFB74D)
                )

                // Survival Rules
                ObjectiveCard(
                    title = "SURVIVAL & STEALTH",
                    description = "• Granny hears everything: dropped items make loud noise.\n• Grandpa patrols with a shotgun: keep your distance.\n• Sledrina appears randomly: look away immediately!\n• Crouch to muffle footsteps and hide inside wardrobes or under beds.",
                    color = Color(0xFF81D4FA)
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "UNDERSTOOD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectiveCard(
    title: String,
    description: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33263238), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = description,
            color = Color(0xFFCFD8DC),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

/**
 * Settings dialog for volume, sensitivity, difficulty, and background presentation.
 */
@Composable
private fun HorrorSettingsDialog(
    sfxEnabled: Boolean,
    onSfxChange: (Boolean) -> Unit,
    ambienceEnabled: Boolean,
    onAmbienceChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    useKeyartBackdrop: Boolean,
    onToggleBackdrop: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141318)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B0000)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GAME SETTINGS",
                        color = Color(0xFFFF1744),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Serif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Divider(color = Color(0x448B0000), thickness = 1.dp)

                // Audio Toggles
                SettingToggleRow(
                    label = "Sound Effects (SFX)",
                    checked = sfxEnabled,
                    onCheckedChange = onSfxChange
                )

                SettingToggleRow(
                    label = "Ambience & Music",
                    checked = ambienceEnabled,
                    onCheckedChange = onAmbienceChange
                )

                SettingToggleRow(
                    label = "Vibration & Haptics",
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsChange
                )

                // Background Style
                SettingToggleRow(
                    label = "Cinematic Poster Matte",
                    checked = useKeyartBackdrop,
                    onCheckedChange = onToggleBackdrop
                )

                // Touch Sensitivity
                Column {
                    Text(
                        text = "Touch Look Sensitivity: ${(sensitivity * 100).toInt()}%",
                        color = Color(0xFFECEFF1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF1744),
                            activeTrackColor = Color(0xFFD50000),
                            inactiveTrackColor = Color(0x44FFFFFF)
                        )
                    )
                }

                // Difficulty Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Nightmare Difficulty",
                        color = Color(0xFFECEFF1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("EASY", "NORMAL", "NIGHTMARE").forEach { diff ->
                            val isSelected = difficulty == diff
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) Color(0xFF8B0000) else Color(0x33263238)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFFF5252) else Color(0x22FFFFFF),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onDifficultyChange(diff) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = diff,
                                    color = if (isSelected) Color.White else Color(0xFFB0BEC5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SAVE & CLOSE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFECEFF1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFD50000),
                uncheckedThumbColor = Color(0xFF9E9E9E),
                uncheckedTrackColor = Color(0x44263238)
            )
        )
    }
}

/**
 * Android system quit confirmation dialog.
 */
@Composable
private fun QuitConfirmationDialog(
    onConfirmQuit: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141318)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B0000)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ABANDON THE FAMHOUSE?",
                    color = Color(0xFFFF1744),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Are you sure you want to exit the game? Granny and the family will be waiting for your return...",
                    color = Color(0xFFCFD8DC),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFECEFF1)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "STAY",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Button(
                        onClick = onConfirmQuit,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("confirm_quit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD50000)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "QUIT",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Jumpscare full-screen overlay when Sledrina appears.
 */
@Composable
fun SledrinaScareOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x776A0000))
            .border(8.dp, Color(0xDDFF1744))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DON'T LOOK AWAY!",
                color = Color(0xFFFF1744),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "SLEDRINA IS WATCHING",
                color = Color.White,
                fontSize = 16.sp,
                letterSpacing = 6.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Pause Dialog.
 */
@Composable
fun PauseDialog(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF8B0000), Color(0xFF424242)))),
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PAUSED",
                    color = Color(0xFFFF5252),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESUME", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESTART DAY", color = Color.White)
                }

                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("MAIN MENU", color = Color(0xFFFF8A80))
                }
            }
        }
    }
}

/**
 * Game Over Screen.
 */
@Composable
fun GameOverOverlay(
    onRetry: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF00F0000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "YOU WERE CAUGHT",
                color = Color(0xFFFF1744),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "Granny didn't let you leave the farmhouse...",
                color = Color(0xFFFFCDD2),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                modifier = Modifier
                    .width(220.dp)
                    .height(50.dp)
                    .testTag("retry_button")
            ) {
                Text("TRY AGAIN", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onMainMenu,
                modifier = Modifier.width(220.dp)
            ) {
                Text("MAIN MENU", color = Color.White)
            }
        }
    }
}

/**
 * Win Screen.
 */
@Composable
fun WinOverlay(
    onPlayAgain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0051205)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "YOU ESCAPED!",
                color = Color(0xFF69F0AE),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "You repaired the car and broke through the Farmhouse gates into freedom!",
                color = Color(0xFFB9F6CA),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlayAgain,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                modifier = Modifier
                    .width(220.dp)
                    .height(50.dp)
                    .testTag("play_again_button")
            ) {
                Text("PLAY AGAIN", fontWeight = FontWeight.Bold)
            }
        }
    }
}
