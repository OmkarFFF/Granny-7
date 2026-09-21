package com.example.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.ai.*
import com.example.audio.HorrorAudioEngine
import com.example.game.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

class GrannyRenderer(
    private val context: Context,
    val world: FarmhouseWorld,
    val audio: HorrorAudioEngine,
    val onStateChange: (GameState) -> Unit,
    val onScareChange: (Boolean) -> Unit,
    val onNotification: (String) -> Unit
) : GLSurfaceView.Renderer {

    var currentGameState = GameState.MAIN_MENU

    // Player Camera & Physics
    var playerPos = Vector3(0f, 0f, 6.0f) // Start at main entrance
    var playerYaw = 0f
    var playerPitch = 0f
    var isCrouching = false
    var currentHidingSpot: HidingSpot? = null
    var playerHiding = HidingType.NONE

    // Flashlight & Inventory
    var isFlashlightOn = true
    val inventory = ArrayList<WorldItem>()
    var selectedItemIndex = 0
    var playerDays = 1
    val maxDays = 5
    var noiseLevel = 0f

    // Input state from HUD
    var moveJoystickX = 0f
    var moveJoystickY = 0f
    var lookDeltaX = 0f
    var lookDeltaY = 0f

    // Screen dimensions
    private var screenWidth = 1920
    private var screenHeight = 1080

    // Shaders & Meshes
    private val shader = HorrorShader()
    private val cubeMesh get() = MeshFactory.createCube()
    private val doorMesh get() = MeshFactory.createDoorMesh()
    private val tableMesh get() = MeshFactory.createTableMesh()
    private val bedMesh get() = MeshFactory.createBedMesh()
    private val wardrobeMesh get() = MeshFactory.createWardrobeMesh()
    private val bookshelfMesh get() = MeshFactory.createBookshelfMesh()
    private val kitchenMesh get() = MeshFactory.createKitchenCounterMesh()
    private val bathtubMesh get() = MeshFactory.createBathtubMesh()
    private val vehicleMesh get() = MeshFactory.createVehicleMesh()
    private val wellMesh get() = MeshFactory.createWellMesh()
    private val fenceMesh get() = MeshFactory.createFenceMesh()
    private val gateMesh get() = MeshFactory.createGateMesh()
    private val treeMesh get() = MeshFactory.createTreeMesh()
    private val chainedChestMesh get() = MeshFactory.createChainedChestMesh()

    private val grannyMesh get() = MeshFactory.createGrannyMesh()
    private val grandpaMesh get() = MeshFactory.createGrandpaMesh()
    private val sledermanMesh get() = MeshFactory.createSledermanMesh()
    private val sledrinaMesh get() = MeshFactory.createSledrinaMesh()
    private val angeleneMesh get() = MeshFactory.createAngeleneMesh()

    private fun getItemMesh(meshIndex: Int): Mesh = MeshFactory.createItemMesh(meshIndex)

    // AI Enemies
    val grannyAI = GrannyAI(Vector3(-6.5f, 0f, -5.0f))
    val grandpaAI = GrandpaAI(Vector3(6.5f, 0f, -2.0f))
    val sledermanAI = SledermanAI(Vector3(-14.0f, 0f, 14.0f))
    val angeleneAI = AngeleneAI(Vector3(1.5f, -3.5f, -4.5f))

    // Matrices
    private val projMatrix = Matrix4()
    private val viewMatrix = Matrix4()
    private val modelMatrix = Matrix4()
    private val mvpMatrix = Matrix4()
    private val normalMatrix = Matrix4()

    private var lastFrameTime = System.nanoTime()
    var menuAnimTime = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.025f, 0.035f, 1.0f) // Deep midnight horror fog
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        shader.create()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / if (height > 0) height.toFloat() else 1f
        projMatrix.setPerspective(68.0f, aspect, 0.1f, 80.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1_000_000_000.0f).coerceIn(0.001f, 0.1f)
        lastFrameTime = now

        if (currentGameState == GameState.GAMEPLAY || currentGameState == GameState.SLEDRINA_SCARE) {
            updateGame(dt)
        } else if (currentGameState == GameState.MAIN_MENU) {
            menuAnimTime += dt
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (currentGameState == GameState.MAIN_MENU) {
            renderMainMenuScene()
        } else {
            renderScene()
        }
    }

    private fun updateGame(dt: Float) {
        // 1. Sledrina Appearance Event Countdown (~every 70s)
        val sledrina = world.sledrinaState
        if (!sledrina.isScaring) {
            sledrina.timerUntilNextAppearance -= dt
            if (sledrina.timerUntilNextAppearance <= 0f) {
                // Trigger Sledrina jumpscare event!
                sledrina.isScaring = true
                sledrina.scareDuration = 8.5f
                sledrina.timerUntilNextAppearance = 75f

                // Position Sledrina 1.3m directly in front of the player's face
                val rad = Math.toRadians(playerYaw.toDouble())
                val forwardX = -sin(rad).toFloat()
                val forwardZ = cos(rad).toFloat()
                sledrina.scarePosition.set(
                    playerPos.x + forwardX * 1.25f,
                    playerPos.y,
                    playerPos.z + forwardZ * 1.25f
                )
                sledrina.scareRotationY = playerYaw + 180f

                audio.playSledrinaScare()
                currentGameState = GameState.SLEDRINA_SCARE
                onScareChange(true)
                onStateChange(GameState.SLEDRINA_SCARE)
            }
        } else {
            // Sledrina scare is active - player movement is locked!
            sledrina.scareDuration -= dt
            // Camera shake during scare
            playerYaw += (Random.nextFloat() - 0.5f) * 1.5f
            playerPitch += (Random.nextFloat() - 0.5f) * 1.2f

            if (sledrina.scareDuration <= 0f) {
                sledrina.isScaring = false
                currentGameState = GameState.GAMEPLAY
                onScareChange(false)
                onStateChange(GameState.GAMEPLAY)
            }
            return
        }

        // 2. Camera Look rotation from touch delta
        playerYaw += lookDeltaX * 0.22f
        playerPitch = (playerPitch + lookDeltaY * 0.22f).coerceIn(-75f, 75f)
        lookDeltaX = 0f
        lookDeltaY = 0f

        // 3. Player Movement (if not hiding in wardrobe or scare-locked)
        if (playerHiding == HidingType.NONE) {
            val speed = if (isCrouching) 2.0f else 3.8f
            val rad = Math.toRadians(playerYaw.toDouble())
            val forwardX = -sin(rad).toFloat()
            val forwardZ = cos(rad).toFloat()
            val rightX = cos(rad).toFloat()
            val rightZ = sin(rad).toFloat()

            val dx = (forwardX * moveJoystickY + rightX * moveJoystickX) * speed * dt
            val dz = (forwardZ * moveJoystickY + rightZ * moveJoystickX) * speed * dt

            val isMoving = abs(moveJoystickX) > 0.1f || abs(moveJoystickY) > 0.1f
            if (isMoving) {
                val newX = playerPos.x + dx
                val newZ = playerPos.z + dz

                if (!world.isPositionBlocked(newX, playerPos.y, newZ)) {
                    playerPos.x = newX
                    playerPos.z = newZ
                } else {
                    if (!world.isPositionBlocked(newX, playerPos.y, playerPos.z)) {
                        playerPos.x = newX
                    } else if (!world.isPositionBlocked(playerPos.x, playerPos.y, newZ)) {
                        playerPos.z = newZ
                    }
                }

                // Footstep sound & noise level
                noiseLevel = if (isCrouching) 15f else 50f
                if (Random.nextFloat() < (if (isCrouching) 0.04f else 0.08f)) {
                    audio.playFootstep(isCrouching = isCrouching, isRunning = false)
                }
            } else {
                noiseLevel = max(0f, noiseLevel - dt * 35f)
            }
        }

        // 4. Update Enemy AIs
        grannyAI.update(dt, playerPos, playerHiding, world, audio) { damage, msg ->
            handlePlayerDamage(msg)
        }
        grandpaAI.update(dt, playerPos, playerHiding, world, audio) { damage, msg ->
            handlePlayerDamage(msg)
        }
        sledermanAI.update(dt, playerPos, playerHiding, world, audio) { damage, msg ->
            handlePlayerDamage(msg)
        }
        angeleneAI.update(dt, playerPos, playerHiding, world, audio) { damage, msg ->
            handlePlayerDamage(msg)
        }

        // 5. Vehicle Escape cutscene if started
        val car = world.vehicle
        if (car.carStarted) {
            car.escapeCutsceneTime += dt
            car.position.z += dt * 5.5f // car drives forward through gate!
            if (car.escapeCutsceneTime > 4.0f) {
                currentGameState = GameState.WIN
                onStateChange(GameState.WIN)
            }
        }
    }

    private fun handlePlayerDamage(msg: String) {
        playerDays++
        if (playerDays > maxDays) {
            currentGameState = GameState.GAME_OVER
            onStateChange(GameState.GAME_OVER)
            onNotification("Game Over - Granny caught you!")
        } else {
            // Wake up on next day
            playerPos.set(0f, 0f, 6.0f)
            playerHiding = HidingType.NONE
            currentHidingSpot = null
            grannyAI.position.set(-6.5f, 0f, -5.0f)
            grannyAI.state = EnemyState.PATROL
            grandpaAI.position.set(6.5f, 0f, -2.0f)
            grandpaAI.state = EnemyState.PATROL
            onNotification("$msg - Day $playerDays of $maxDays")
        }
    }

    private fun renderMainMenuScene() {
        shader.use()

        // 1. Cinematic Camera: Front yard looking at haunted farmhouse facade with subtle breathing sway
        val camX = sin(menuAnimTime * 0.25f) * 0.7f
        val camY = 1.90f + cos(menuAnimTime * 0.35f) * 0.10f
        val camZ = 16.5f + sin(menuAnimTime * 0.18f) * 0.35f
        val targetY = 2.4f
        val targetZ = 6.0f

        viewMatrix.setLookAt(
            camX, camY, camZ,
            0f, targetY, targetZ,
            0f, 1f, 0f
        )

        val lookDir = Vector3(0f - camX, targetY - camY, targetZ - camZ).normalized()
        GLES20.glUniform3f(shader.uCameraPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(shader.uFlashlightDirLoc, lookDir.x, lookDir.y, lookDir.z)
        GLES20.glUniform1f(shader.uFlashlightActiveLoc, 0.0f) // Moonlight only

        // Cold blue moonlight from full moon in upper-right sky
        GLES20.glUniform3f(shader.uAmbientLightLoc, 0.24f, 0.26f, 0.34f)
        GLES20.glUniform3f(shader.uMoonLightDirLoc, -0.45f, -0.75f, -0.45f)
        GLES20.glUniform3f(shader.uMoonLightColorLoc, 0.38f, 0.45f, 0.68f)

        // Flickering warm lantern light on stone gate post
        val flicker = 0.95f + sin(menuAnimTime * 14.5f) * 0.08f + cos(menuAnimTime * 7.3f) * 0.05f
        GLES20.glUniform3f(shader.uHorrorGlowPosLoc, -3.5f, 1.8f, 13.5f)
        GLES20.glUniform3f(shader.uHorrorGlowColorLoc, 1.0f * flicker, 0.80f * flicker, 0.35f * flicker)
        GLES20.glUniform1f(shader.uHorrorGlowIntensityLoc, 1.8f)

        // Atmospheric Distance Fog
        GLES20.glUniform3f(shader.uFogColorLoc, 0.025f, 0.030f, 0.045f)
        GLES20.glUniform1f(shader.uFogStartLoc, 14.0f)
        GLES20.glUniform1f(shader.uFogEndLoc, 50.0f)
        GLES20.glUniform1f(shader.uUseObjectColorLoc, 0.0f)

        // 2. Render Farmhouse Architecture & Yard
        renderArchitecture()

        // Extra Farmhouse Details for the Cinematic Menu:
        // Brick Chimney
        drawBox(4.2f, 8.2f, 1.5f, 1.0f, 2.6f, 1.0f, 0.45f, 0.18f, 0.14f)
        // Lit Warm Windows on Farmhouse Facade
        val windowGlow = 0.9f + sin(menuAnimTime * 5.0f) * 0.05f
        drawBox(-5.7f, 2.0f, 8.16f, 1.5f, 1.5f, 0.04f, 0.95f * windowGlow, 0.82f * windowGlow, 0.35f)
        drawBox(5.7f, 2.0f, 8.16f, 1.5f, 1.5f, 0.04f, 0.95f * windowGlow, 0.82f * windowGlow, 0.35f)
        drawBox(0f, 5.25f, 8.16f, 1.8f, 1.6f, 0.04f, 0.98f * windowGlow, 0.85f * windowGlow, 0.40f)

        // Stone post with glowing lantern
        drawBox(-3.5f, 0.8f, 13.5f, 0.6f, 1.6f, 0.6f, 0.35f, 0.32f, 0.30f)
        drawBox(-3.5f, 1.8f, 13.5f, 0.32f, 0.42f, 0.32f, 1.0f, 0.85f, 0.40f)

        // 3. Render Props: Trees, Well, Fence, Car
        renderFurniture()

        // 4. Render All Five Characters in the Exact Poster Composition:
        val bob = sin(menuAnimTime * 2.0f) * 0.02f

        // Granny: Center foreground with bloody club
        drawMeshAt(grannyMesh, 0.0f, bob, 12.6f, rotY = 180f)

        // Grandpa: Standing to Granny's right with shotgun
        drawMeshAt(grandpaMesh, 1.25f, 0f, 12.7f, rotY = 180f)

        // Slederman: Tall figure to Granny's left in black suit with tentacles
        drawMeshAt(sledermanMesh, -1.25f, 0f, 12.7f, rotY = 180f)

        // Sledrina: Floating ghost right behind Granny with gaping mouth
        val floatSledrina = sin(menuAnimTime * 2.4f) * 0.10f
        drawMeshAt(sledrinaMesh, 0.0f, 1.25f + floatSledrina, 11.2f, rotY = 180f)

        // Angelene: Far right ghostly grandmother in withered gown
        drawMeshAt(angeleneMesh, 2.40f, bob * 0.5f, 12.1f, rotY = 175f)
    }

    private fun renderScene() {
        shader.use()

        // Calculate eye position & look vector
        val eyeHeight = when (playerHiding) {
            HidingType.UNDER_BED -> 0.35f
            HidingType.WARDROBE -> 1.15f
            else -> if (isCrouching) 0.85f else 1.70f
        }
        val eyePos = Vector3(playerPos.x, playerPos.y + eyeHeight, playerPos.z)

        val yawRad = Math.toRadians(playerYaw.toDouble())
        val pitchRad = Math.toRadians(playerPitch.toDouble())
        val lookDir = Vector3(
            (-sin(yawRad) * cos(pitchRad)).toFloat(),
            sin(pitchRad).toFloat(),
            (cos(yawRad) * cos(pitchRad)).toFloat()
        ).normalized()

        val target = eyePos + lookDir
        viewMatrix.setLookAt(
            eyePos.x, eyePos.y, eyePos.z,
            target.x, target.y, target.z,
            0f, 1f, 0f
        )

        // Set Lighting & Atmosphere Uniforms
        GLES20.glUniform3f(shader.uCameraPosLoc, eyePos.x, eyePos.y, eyePos.z)
        GLES20.glUniform3f(shader.uFlashlightDirLoc, lookDir.x, lookDir.y, lookDir.z)
        GLES20.glUniform1f(shader.uFlashlightActiveLoc, if (isFlashlightOn) 1.0f else 0.0f)

        // Ambient & Moonlight
        GLES20.glUniform3f(shader.uAmbientLightLoc, 0.16f, 0.17f, 0.22f)
        GLES20.glUniform3f(shader.uMoonLightDirLoc, -0.4f, -0.8f, -0.4f)
        GLES20.glUniform3f(shader.uMoonLightColorLoc, 0.22f, 0.25f, 0.32f)

        // Horror Glow: Eerie red light around Granny / Sledrina if close
        val grannyDist = eyePos.distanceTo(grannyAI.position)
        if (grannyDist < 9.0f) {
            GLES20.glUniform3f(shader.uHorrorGlowPosLoc, grannyAI.position.x, grannyAI.position.y + 1.5f, grannyAI.position.z)
            GLES20.glUniform3f(shader.uHorrorGlowColorLoc, 0.85f, 0.12f, 0.12f)
            GLES20.glUniform1f(shader.uHorrorGlowIntensityLoc, (1.0f - grannyDist / 9.0f) * 1.5f)
        } else {
            GLES20.glUniform1f(shader.uHorrorGlowIntensityLoc, 0.0f)
        }

        // Distance Fog
        GLES20.glUniform3f(shader.uFogColorLoc, 0.025f, 0.025f, 0.035f)
        GLES20.glUniform1f(shader.uFogStartLoc, 12.0f)
        GLES20.glUniform1f(shader.uFogEndLoc, 42.0f)
        GLES20.glUniform1f(shader.uUseObjectColorLoc, 0.0f)

        // 1. Render Farmhouse Architecture
        renderArchitecture()

        // 2. Render Furniture & Props
        renderFurniture()

        // 3. Render Interactive Doors
        renderDoors()

        // 4. Render World Items
        renderItems()

        // 5. Render Characters
        renderCharacters()
    }

    private fun renderArchitecture() {
        // Ground Floor (Y = 0)
        drawBox(0f, -0.05f, 0f, 20f, 0.1f, 18f, 0.28f, 0.22f, 0.16f) // Wood floor plank
        // Ceiling / 1st Floor (Y = 3.5)
        drawBox(0f, 3.5f, 0f, 20f, 0.1f, 18f, 0.24f, 0.20f, 0.15f)
        // Roof (Y = 7.0)
        drawBox(0f, 7.0f, 0f, 21f, 0.3f, 19f, 0.18f, 0.16f, 0.15f)
        // Basement Floor (Y = -3.5)
        drawBox(0f, -3.55f, -2f, 16f, 0.1f, 12f, 0.18f, 0.18f, 0.20f) // Damp stone

        // Exterior Yard Ground
        drawBox(0f, -0.08f, 14f, 50f, 0.05f, 50f, 0.12f, 0.15f, 0.10f) // Dark grass / dirt

        // Exterior Porch
        drawBox(0f, 0.25f, 9.5f, 8f, 0.5f, 3f, 0.32f, 0.25f, 0.18f)
        // Porch Pillars
        drawBox(-3.8f, 1.75f, 10.8f, 0.2f, 3.0f, 0.2f, 0.30f, 0.24f, 0.18f)
        drawBox(3.8f, 1.75f, 10.8f, 0.2f, 3.0f, 0.2f, 0.30f, 0.24f, 0.18f)

        // House Outer Walls: Weathered dark wooden planks (matches Image 6)
        val wallR = 0.32f; val wallG = 0.28f; val wallB = 0.24f
        // South front wall with door gap
        drawBox(-5.7f, 1.75f, 8f, 8.6f, 3.5f, 0.3f, wallR, wallG, wallB)
        drawBox(5.7f, 1.75f, 8f, 8.6f, 3.5f, 0.3f, wallR, wallG, wallB)
        drawBox(0f, 3.0f, 8f, 2.8f, 1.0f, 0.3f, wallR, wallG, wallB) // above door

        // North back wall
        drawBox(0f, 1.75f, -10f, 20f, 3.5f, 0.3f, wallR, wallG, wallB)
        // West side wall
        drawBox(-10f, 1.75f, -1f, 0.3f, 3.5f, 18f, wallR, wallG, wallB)
        // East side wall
        drawBox(10f, 1.75f, -1f, 0.3f, 3.5f, 18f, wallR, wallG, wallB)

        // Upper Floor Outer Walls (Y = 3.5 to 7.0)
        drawBox(0f, 5.25f, 8f, 20f, 3.5f, 0.3f, wallR * 0.95f, wallG * 0.95f, wallB * 0.95f)
        drawBox(0f, 5.25f, -10f, 20f, 3.5f, 0.3f, wallR * 0.95f, wallG * 0.95f, wallB * 0.95f)
        drawBox(-10f, 5.25f, -1f, 0.3f, 3.5f, 18f, wallR * 0.95f, wallG * 0.95f, wallB * 0.95f)
        drawBox(10f, 5.25f, -1f, 0.3f, 3.5f, 18f, wallR * 0.95f, wallG * 0.95f, wallB * 0.95f)

        // Interior Partitions (Ground Floor):
        drawBox(-3.5f, 1.75f, 0.5f, 0.2f, 3.5f, 5.0f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)
        drawBox(-3.5f, 1.75f, 6.25f, 0.2f, 3.5f, 3.5f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)
        drawBox(-8.25f, 1.75f, -2f, 3.5f, 3.5f, 0.2f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)
        drawBox(-4.25f, 1.75f, -2f, 1.5f, 3.5f, 0.2f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)
        drawBox(4.5f, 1.75f, 0.5f, 0.2f, 3.5f, 5.0f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)
        drawBox(4.5f, 1.75f, 6.5f, 0.2f, 3.5f, 3.0f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)
        drawBox(6.0f, 1.75f, 3f, 3.0f, 3.5f, 0.2f, wallR * 0.9f, wallG * 0.9f, wallB * 0.9f)

        // Staircases:
        // Central stairs to 1st floor (X = 0, Z = -2 to 2)
        for (step in 0..11) {
            val sy = step * 0.30f
            val sz = -step * 0.35f
            drawBox(0f, sy + 0.15f, sz, 1.6f, 0.30f, 0.35f, 0.36f, 0.26f, 0.18f)
        }
        // Stairs down to basement (X = 2.5, Z = -2 to -5)
        for (step in 0..11) {
            val sy = -step * 0.30f
            val sz = -2f - step * 0.35f
            drawBox(2.5f, sy - 0.15f, sz, 1.4f, 0.30f, 0.35f, 0.24f, 0.22f, 0.22f)
        }
    }

    private fun renderFurniture() {
        // 1. Living room sofa & coffee table
        drawMeshAt(tableMesh, 0f, 0f, 0.5f)
        drawBox(0f, 0.45f, 2.4f, 2.4f, 0.9f, 1.1f, 0.28f, 0.32f, 0.40f) // sofa

        // 2. Kitchen counters & stove
        drawMeshAt(kitchenMesh, -8.5f, 0f, 1.2f)

        // 3. Dining table & chairs
        drawMeshAt(tableMesh, -6.5f, 0f, -6.0f, scale = 1.3f)

        // 4. Bathroom tub
        drawMeshAt(bathtubMesh, 8.5f, 0f, 1.2f)

        // 5. Upstairs Beds & Wardrobes
        drawMeshAt(bedMesh, -7.5f, 3.5f, 4.0f) // Bedroom 1 bed
        drawMeshAt(wardrobeMesh, -4.5f, 3.5f, 6.5f, rotY = 180f) // Bedroom 1 wardrobe (hiding spot)

        drawMeshAt(bedMesh, -7.5f, 3.5f, -5.0f) // Bedroom 2 bed
        drawMeshAt(wardrobeMesh, -4.5f, 3.5f, -8.5f) // Bedroom 2 wardrobe (hiding spot)

        drawMeshAt(bedMesh, 6.1f, 3.5f, -5.0f, rotY = 90f) // Bedroom 3 bed (crawlspace under bed!)

        // 6. Library / Study Bookshelf & Desk
        drawMeshAt(bookshelfMesh, 5.5f, 3.5f, -9.5f)
        drawMeshAt(bookshelfMesh, 8.5f, 3.5f, -9.5f)
        drawMeshAt(tableMesh, 5.5f, 3.5f, -2.0f)

        // 7. Basement Angelene Chained Chest
        drawMeshAt(chainedChestMesh, 1.5f, -3.5f, -6.0f)

        // 8. Exterior Yard:
        // Escape Car
        drawMeshAt(vehicleMesh, world.vehicle.position.x, world.vehicle.position.y, world.vehicle.position.z)

        // Water Well
        drawMeshAt(wellMesh, -8.0f, 0f, 16.0f)

        // Wooden Shed
        drawBox(-14.0f, 1.75f, 14.0f, 5.0f, 3.5f, 5.0f, 0.28f, 0.18f, 0.10f)

        // Wooden Fences along perimeter
        for (fx in -24..24 step 3) {
            drawMeshAt(fenceMesh, fx.toFloat(), 0f, -20.5f) // North fence
        }
        for (fz in -20..27 step 3) {
            drawMeshAt(fenceMesh, -25.5f, 0f, fz.toFloat(), rotY = 90f) // West fence
            drawMeshAt(fenceMesh, 25.5f, 0f, fz.toFloat(), rotY = 90f)  // East fence
        }
        for (fx in -24..-4 step 3) {
            drawMeshAt(fenceMesh, fx.toFloat(), 0f, 28.0f) // South fence left
        }
        for (fx in 4..24 step 3) {
            drawMeshAt(fenceMesh, fx.toFloat(), 0f, 28.0f) // South fence right
        }

        // Main Escape Gate at (0, 0, 28)
        drawMeshAt(gateMesh, 0f, 0f, 28.0f)

        // Forest Trees in distance
        val treeCoords = arrayOf(
            floatArrayOf(-22f, 22f), floatArrayOf(-18f, 25f), floatArrayOf(-12f, 26f),
            floatArrayOf(12f, 26f), floatArrayOf(18f, 24f), floatArrayOf(22f, 22f),
            floatArrayOf(-28f, 5f), floatArrayOf(-28f, -10f), floatArrayOf(28f, 5f),
            floatArrayOf(28f, -10f), floatArrayOf(-15f, -24f), floatArrayOf(0f, -24f),
            floatArrayOf(15f, -24f)
        )
        for (tc in treeCoords) {
            drawMeshAt(treeMesh, tc[0], 0f, tc[1])
        }
    }

    private fun renderDoors() {
        for (door in world.doors) {
            drawMeshAt(doorMesh, door.position.x, door.position.y, door.position.z, rotY = door.currentAngle)
        }
    }

    private fun renderItems() {
        val hoverY = sin((System.currentTimeMillis() % 2000) / 2000.0 * 2.0 * Math.PI).toFloat() * 0.04f
        val spinY = ((System.currentTimeMillis() % 3600) / 3600.0 * 360.0).toFloat()

        for (item in world.items) {
            if (item.isInInventory || item.isUsed) continue
            val mesh = getItemMesh(item.type.meshIndex)
            drawMeshAt(mesh, item.position.x, item.position.y + hoverY, item.position.z, rotY = spinY)
        }
    }

    private fun renderCharacters() {
        // 1. Granny (Image 1)
        val gWalkSway = sin(grannyAI.stateTimer * 8.0).toFloat() * 4.0f
        drawMeshAt(grannyMesh, grannyAI.position.x, grannyAI.position.y, grannyAI.position.z, rotY = grannyAI.rotationY + gWalkSway)

        // 2. Grandpa (Image 2)
        drawMeshAt(grandpaMesh, grandpaAI.position.x, grandpaAI.position.y, grandpaAI.position.z, rotY = grandpaAI.rotationY)

        // 3. Slederman (Image 4)
        drawMeshAt(sledermanMesh, sledermanAI.position.x, sledermanAI.position.y, sledermanAI.position.z, rotY = sledermanAI.rotationY)

        // 4. Angelene (Image 5) - rendered if freed
        if (angeleneAI.isActiveInWorld) {
            drawMeshAt(angeleneMesh, angeleneAI.position.x, angeleneAI.position.y, angeleneAI.position.z, rotY = angeleneAI.rotationY)
        }

        // 5. Sledrina (Image 3) - Jumpscare event entity floating right in front of the camera!
        if (world.sledrinaState.isScaring) {
            val sc = world.sledrinaState
            val shakeX = (Random.nextFloat() - 0.5f) * 0.08f
            val shakeY = (Random.nextFloat() - 0.5f) * 0.08f
            drawMeshAt(
                sledrinaMesh,
                sc.scarePosition.x + shakeX,
                sc.scarePosition.y + shakeY,
                sc.scarePosition.z,
                rotY = sc.scareRotationY
            )
        }
    }

    private fun drawBox(
        cx: Float, cy: Float, cz: Float,
        sizeX: Float, sizeY: Float, sizeZ: Float,
        r: Float, g: Float, b: Float, a: Float = 1.0f
    ) {
        modelMatrix.setIdentity()
        modelMatrix.translate(cx, cy, cz)
        modelMatrix.scale(sizeX, sizeY, sizeZ)
        updateMatrices()

        GLES20.glUniform1f(shader.uUseObjectColorLoc, 1.0f)
        GLES20.glUniform4f(shader.uObjectColorLoc, r, g, b, a)
        cubeMesh.render(shader)
        GLES20.glUniform1f(shader.uUseObjectColorLoc, 0.0f)
    }

    private fun drawMeshAt(mesh: Mesh, x: Float, y: Float, z: Float, rotY: Float = 0f, scale: Float = 1f) {
        modelMatrix.setIdentity()
        modelMatrix.translate(x, y, z)
        if (rotY != 0f) modelMatrix.rotate(rotY, 0f, 1f, 0f)
        if (scale != 1f) modelMatrix.scale(scale, scale, scale)
        updateMatrices()

        mesh.render(shader)
    }

    private fun updateMatrices() {
        mvpMatrix.multiply(projMatrix, viewMatrix)
        mvpMatrix.multiply(mvpMatrix, modelMatrix)

        normalMatrix.set(modelMatrix)

        GLES20.glUniformMatrix4fv(shader.uMVPMatrixLoc, 1, false, mvpMatrix.values, 0)
        GLES20.glUniformMatrix4fv(shader.uModelMatrixLoc, 1, false, modelMatrix.values, 0)
        GLES20.glUniformMatrix4fv(shader.uNormalMatrixLoc, 1, false, normalMatrix.values, 0)
    }

    // Interactive Raycast / Hand Action
    fun performInteraction(): String {
        if (currentGameState != GameState.GAMEPLAY) return ""

        // If player is currently hiding, exit hiding
        if (playerHiding != HidingType.NONE) {
            playerHiding = HidingType.NONE
            currentHidingSpot = null
            audio.playDoorCreak()
            return "Exited hiding spot"
        }

        val forwardX = -sin(Math.toRadians(playerYaw.toDouble())).toFloat()
        val forwardZ = cos(Math.toRadians(playerYaw.toDouble())).toFloat()
        val reachPos = playerPos + Vector3(forwardX * 1.5f, 0.8f, forwardZ * 1.5f)

        // 1. Check Hiding Spots (Wardrobes / Beds)
        for (spot in world.hidingSpots) {
            if (playerPos.distanceTo(spot.position) < spot.interactionRadius) {
                currentHidingSpot = spot
                playerHiding = spot.type
                audio.playDoorCreak()
                return when (spot.type) {
                    HidingType.WARDROBE -> "Hiding inside wardrobe"
                    HidingType.UNDER_BED -> "Crawled under bed"
                    else -> "Hiding"
                }
            }
        }

        // 2. Check Interactive Doors
        for (door in world.doors) {
            if (playerPos.distanceTo(door.position) < 2.0f) {
                if (door.isLocked) {
                    val hasKey = inventory.any { it.type == door.requiredKey }
                    if (hasKey) {
                        door.isLocked = false
                        door.isOpen = true
                        door.currentAngle = 85f * door.swingDirection
                        audio.playDoorCreak()
                        return "Unlocked and opened ${door.name}"
                    } else {
                        audio.playDrawerOpen()
                        return "${door.name} is locked! Requires ${door.requiredKey?.displayName}"
                    }
                } else {
                    door.isOpen = !door.isOpen
                    door.currentAngle = if (door.isOpen) 85f * door.swingDirection else 0f
                    audio.playDoorCreak()
                    return if (door.isOpen) "Opened ${door.name}" else "Closed ${door.name}"
                }
            }
        }

        // 3. Check World Items (Pickup)
        for (item in world.items) {
            if (!item.isInInventory && !item.isUsed && playerPos.distanceTo(item.position) < 1.8f) {
                item.isInInventory = true
                inventory.add(item)
                selectedItemIndex = inventory.size - 1
                audio.playItemPickup()
                return "Picked up ${item.type.displayName}"
            }
        }

        // 4. Check Chained Chest (Angelene in basement)
        val chestDist = playerPos.distanceTo(world.angeleneState.chestPosition)
        if (chestDist < 2.2f && world.angeleneState.isChained) {
            val hasCutters = inventory.any { it.type == ItemType.BOLT_CUTTERS || it.type == ItemType.BOX_KEY }
            if (hasCutters) {
                world.angeleneState.isChained = false
                world.angeleneState.isFreed = true
                angeleneAI.position.set(world.angeleneState.chestPosition)
                angeleneAI.isActiveInWorld = true
                audio.playAngeleneFreed()

                // Spawn Car Key right in front of chest as reward!
                world.items.find { it.type == ItemType.CAR_KEY }?.position?.set(
                    world.angeleneState.chestPosition.x,
                    -3.2f,
                    world.angeleneState.chestPosition.z + 1.2f
                )
                return "Freed Angelene! She dropped the Car Key!"
            } else {
                return "Chest is tightly chained! Requires Bolt Cutters or Antique Key"
            }
        }

        // 5. Check Escape Car (Repairs & Start)
        val car = world.vehicle
        val carDist = playerPos.distanceTo(car.position)
        if (carDist < 3.2f) {
            val currentItem = inventory.getOrNull(selectedItemIndex)

            if (currentItem != null) {
                when (currentItem.type) {
                    ItemType.BATTERY -> {
                        car.batteryInstalled = true
                        currentItem.isUsed = true
                        inventory.remove(currentItem)
                        audio.playItemPickup()
                        return "Installed Car Battery!"
                    }
                    ItemType.FUEL_CAN -> {
                        car.fuelAdded = true
                        currentItem.isUsed = true
                        inventory.remove(currentItem)
                        audio.playItemPickup()
                        return "Filled car fuel tank!"
                    }
                    ItemType.ENGINE_PART -> {
                        car.enginePartInstalled = true
                        currentItem.isUsed = true
                        inventory.remove(currentItem)
                        audio.playItemPickup()
                        return "Fitted Engine Part!"
                    }
                    ItemType.MECHANICAL_PART -> {
                        car.mechanicalPartInstalled = true
                        currentItem.isUsed = true
                        inventory.remove(currentItem)
                        audio.playItemPickup()
                        return "Installed Mechanical Part!"
                    }
                    ItemType.CAR_KEY -> {
                        if (!car.isFullyRepaired()) {
                            return "Car is missing parts! Needs Battery, Fuel, Engine & Mechanical parts."
                        }
                        if (!car.isGateFullyOpen()) {
                            return "Main Escape Gate is still locked! Unlock it first."
                        }
                        car.carStarted = true
                        audio.playVehicleEscape()
                        return "Engine started! Driving to escape!"
                    }
                    else -> {}
                }
            }

            // Status check
            return if (!car.isFullyRepaired()) {
                val missing = ArrayList<String>()
                if (!car.batteryInstalled) missing.add("Battery")
                if (!car.fuelAdded) missing.add("Fuel")
                if (!car.enginePartInstalled) missing.add("Engine Part")
                if (!car.mechanicalPartInstalled) missing.add("Mechanical Part")
                "Car needs: " + missing.joinToString(", ")
            } else if (!car.isGateFullyOpen()) {
                "Car is ready! Now unlock and open the Main Gate!"
            } else {
                "Use Car Key to start engine and escape!"
            }
        }

        // 6. Check Main Escape Gate (at X = 0, Z = 28)
        val gateDist = playerPos.distanceTo(Vector3(0f, 0f, 28f))
        if (gateDist < 3.5f) {
            val currentItem = inventory.getOrNull(selectedItemIndex)
            if (currentItem != null) {
                if (currentItem.type == ItemType.BOLT_CUTTERS && !car.gateChainCut) {
                    car.gateChainCut = true
                    audio.playClubAttack()
                    return "Cut gate iron chains with Bolt Cutters!"
                }
                if (currentItem.type == ItemType.GATE_KEY && !car.gateUnlocked) {
                    car.gateUnlocked = true
                    audio.playDoorCreak()
                    return "Unlocked Gate Padlock with Gate Key!"
                }
            }

            return if (!car.gateChainCut) {
                "Gate is chained shut! Requires Bolt Cutters."
            } else if (!car.gateUnlocked) {
                "Gate padlock is locked! Requires Gate Key."
            } else {
                "Gate is fully unlocked and ready for escape!"
            }
        }

        return ""
    }

    // Drop currently selected item (creates noise that alerts Granny!)
    fun dropCurrentItem(): String {
        val item = inventory.getOrNull(selectedItemIndex) ?: return "No item selected"
        inventory.remove(item)
        item.isInInventory = false
        item.position.set(playerPos.x, playerPos.y + 0.1f, playerPos.z)
        if (selectedItemIndex >= inventory.size) selectedItemIndex = max(0, inventory.size - 1)

        audio.playItemDrop()
        // Alert Granny to noise location!
        grannyAI.onNoiseHeard(playerPos, 22.0f)
        return "Dropped ${item.type.displayName} (Noise made!)"
    }
}
