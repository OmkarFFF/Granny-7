package com.example.game

import com.example.engine.AABB
import com.example.engine.Vector3

enum class GameState {
    MAIN_MENU,
    OPTIONS,
    TIPS_STORY,
    LOADING,
    GAMEPLAY,
    PAUSED,
    SLEDRINA_SCARE,
    GAME_OVER,
    WIN
}

enum class DifficultyLevel(val displayName: String, val description: String) {
    PRACTICE(
        "Practice",
        "Granny & Grandpa are not home. You can explore the farmhouse safely, search for items, and practice escaping without threats."
    ),
    EASY(
        "Easy",
        "Granny and Grandpa move slower than you. Dropped objects make less noise. The creaking floors don't alert them as easily."
    ),
    NORMAL(
        "Normal",
        "Granny and Grandpa move at normal speed. Granny hears everything you drop. Farmhouse doors and floorboards creak."
    ),
    HARD(
        "Hard",
        "Granny and Grandpa move faster and are relentless. Granny is more vigilant. Hearing range is significantly increased."
    ),
    EXTREME(
        "Extreme",
        "Granny and Grandpa move faster than the player. Pitch dark environment. One hit causes fatal damage. Only for true horror masters."
    )
}

enum class GameTheme(val displayName: String) {
    ORIGINAL("Original"),
    DARK("Dark"),
    NIGHTMARE("Nightmare")
}

enum class GraphicsQuality(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

data class GameSettings(
    var difficulty: DifficultyLevel = DifficultyLevel.NORMAL,
    var theme: GameTheme = GameTheme.ORIGINAL,
    var darker: Boolean = false,
    var extraLocks: Boolean = false,
    var limping: Boolean = false,
    var music: Boolean = true,
    var soundEffects: Boolean = true,
    var grannyEnabled: Boolean = true,
    var grandpaEnabled: Boolean = true,
    var slendrinaEnabled: Boolean = true,
    var quality: GraphicsQuality = GraphicsQuality.HIGH
)

enum class ItemType(val displayName: String, val description: String, val meshIndex: Int) {
    CAR_KEY("Car Key", "Ignition key for the old vehicle outside.", 0),
    BATTERY("Car Battery", "Heavy 12V battery required to power the car.", 1),
    FUEL_CAN("Fuel Can", "Contains gasoline to fill the car fuel tank.", 2),
    ENGINE_PART("Engine Part", "Crucial component for the car engine block.", 3),
    MECHANICAL_PART("Mechanical Part", "Gear and drive belt for the transmission.", 4),
    GATE_KEY("Gate Key", "Key to unlock the heavy padlock on the main gate.", 0),
    BOLT_CUTTERS("Bolt Cutters", "Heavy tool to cut iron chains and padlocks.", 5),
    BEDROOM_KEY("Bedroom Key", "Brass skeleton key for locked upstairs rooms.", 0),
    BOX_KEY("Antique Key", "Ornate rusted key for the chained basement chest.", 0),
    FLASHLIGHT("Flashlight", "Handheld electric torch to illuminate the darkness.", 6)
}

data class WorldItem(
    val id: String,
    val type: ItemType,
    var position: Vector3,
    var rotationY: Float = 0f,
    var isInInventory: Boolean = false,
    var isUsed: Boolean = false
) {
    val boundingBox: AABB
        get() = AABB(
            position.x - 0.4f, position.y, position.z - 0.4f,
            position.x + 0.4f, position.y + 0.8f, position.z + 0.4f
        )
}

enum class HidingType {
    NONE,
    WARDROBE,
    UNDER_BED
}

data class HidingSpot(
    val id: String,
    val type: HidingType,
    val position: Vector3,
    val interactionRadius: Float = 1.4f,
    val cameraOffset: Vector3 = Vector3(0f, 0f, 0f),
    val lookAngle: Float = 0f
)

data class InteractiveDoor(
    val id: String,
    val name: String,
    val position: Vector3,
    var isOpen: Boolean = false,
    var isLocked: Boolean = false,
    val requiredKey: ItemType? = null,
    val swingDirection: Float = 1f,
    var currentAngle: Float = 0f
) {
    val aabb: AABB
        get() {
            return if (!isOpen) {
                AABB(position.x - 0.5f, position.y, position.z - 0.1f,
                    position.x + 0.5f, position.y + 2.4f, position.z + 0.1f)
            } else {
                AABB(position.x, position.y, position.z, position.x, position.y, position.z) // open door non-blocking
            }
        }
}

/**
 * Escape Vehicle condition tracking.
 */
class EscapeVehicle {
    var position = Vector3(8.5f, 0f, 12f)
    var batteryInstalled = false
    var fuelAdded = false
    var enginePartInstalled = false
    var mechanicalPartInstalled = false
    var gateChainCut = false
    var gateUnlocked = false
    var carStarted = false
    var escapeCutsceneTime = 0f

    fun isFullyRepaired(): Boolean {
        return batteryInstalled && fuelAdded && enginePartInstalled && mechanicalPartInstalled
    }

    fun isGateFullyOpen(): Boolean {
        return gateChainCut && gateUnlocked
    }

    fun canEscape(): Boolean {
        return isFullyRepaired() && isGateFullyOpen()
    }
}

/**
 * Angelene special encounter state.
 */
class AngeleneEventState {
    var isChained = true
    var isFreed = false
    var chestPosition = Vector3(1.5f, -3.5f, -6f)
    var activeTimer = 0f
    var hasDroppedKey = false
}

/**
 * Sledrina scare event state.
 */
class SledrinaEventState {
    var timerUntilNextAppearance = 70f // every ~60-90s
    var isScaring = false
    var scareDuration = 0f
    var scarePosition = Vector3()
    var scareRotationY = 0f
}
