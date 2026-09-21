package com.example.ai

import com.example.audio.HorrorAudioEngine
import com.example.engine.Vector3
import com.example.game.*
import kotlin.math.*
import kotlin.random.Random

enum class EnemyState {
    PATROL,
    INVESTIGATE_NOISE,
    CHASE,
    AIM,
    ATTACK,
    SEARCH
}

/**
 * Base AI controller for entities in the Farmhouse.
 */
abstract class BaseEnemy(
    val name: String,
    var position: Vector3,
    var moveSpeed: Float = 2.2f,
    var chaseSpeed: Float = 3.6f
) {
    var isActive: Boolean = true
    var state = EnemyState.PATROL
    var rotationY = 0f
    var stateTimer = 0f
    var targetPosition = Vector3()
    var currentWaypointIndex = 0
    var lastSeenPlayerPos = Vector3()
    var isPlayerInSight = false
    var walkCycle: Float = 0f

    abstract fun update(
        dt: Float,
        playerPos: Vector3,
        playerHiding: HidingType,
        world: FarmhouseWorld,
        audio: HorrorAudioEngine,
        onPlayerAttacked: (damage: Int, message: String) -> Unit
    )

    protected fun moveTowards(target: Vector3, speed: Float, dt: Float, world: FarmhouseWorld): Boolean {
        val dx = target.x - position.x
        val dz = target.z - position.z
        val dist = sqrt(dx * dx + dz * dz)

        if (dist < 0.3f) return true // Reached target

        rotationY = (atan2(dx.toDouble(), dz.toDouble()) * 180.0 / Math.PI).toFloat()

        val step = speed * dt
        val nextX = position.x + (dx / dist) * step
        val nextZ = position.z + (dz / dist) * step

        if (!world.isPositionBlocked(nextX, position.y, nextZ, 0.4f)) {
            position.x = nextX
            position.z = nextZ
        } else {
            // Slight lateral slide around obstacle
            if (!world.isPositionBlocked(nextX, position.y, position.z, 0.4f)) {
                position.x = nextX
            } else if (!world.isPositionBlocked(position.x, position.y, nextZ, 0.4f)) {
                position.z = nextZ
            }
        }
        return false
    }

    protected fun canSeePlayer(
        playerPos: Vector3,
        playerHiding: HidingType,
        world: FarmhouseWorld,
        viewDistance: Float = 14f,
        viewFovDegrees: Float = 85f
    ): Boolean {
        // Player hidden in wardrobe or under bed cannot be seen
        if (playerHiding != HidingType.NONE) return false

        // Floor height check (must be roughly on same floor level within 1.8m)
        if (abs(position.y - playerPos.y) > 1.8f) return false

        val dist = position.distanceTo(playerPos)
        if (dist > viewDistance) return false

        // FOV Angle check
        val toPlayerX = playerPos.x - position.x
        val toPlayerZ = playerPos.z - position.z
        val angleToPlayer = (atan2(toPlayerX.toDouble(), toPlayerZ.toDouble()) * 180.0 / Math.PI).toFloat()
        var angleDiff = abs(angleToPlayer - rotationY)
        while (angleDiff > 180f) angleDiff = abs(angleDiff - 360f)

        if (angleDiff > viewFovDegrees * 0.5f && dist > 2.0f) {
            return false // outside cone of vision
        }

        // Raycast line of sight check (cannot see through walls)
        return world.hasLineOfSight(position + Vector3(0f, 1.4f, 0f), playerPos + Vector3(0f, 1.2f, 0f))
    }
}

/**
 * Granny AI:
 * Patrols -> Sees player -> Chases -> Club attack -> Searches on lose sight -> Patrols.
 * Investigates noise from dropped objects. Cannot see through walls or teleport.
 */
class GrannyAI(startPos: Vector3) : BaseEnemy("Granny", startPos, moveSpeed = 2.2f, chaseSpeed = 3.8f) {

    var isAttacking = false
    var attackAnimTimer = 0f
    private var attackCooldown = 0f
    private var hasDealtDamage = false

    fun onNoiseHeard(noisePos: Vector3, loudness: Float) {
        if (!isActive) return
        if (state == EnemyState.CHASE || isAttacking) return // busy chasing
        val dist = position.distanceTo(noisePos)
        if (dist < loudness) {
            state = EnemyState.INVESTIGATE_NOISE
            targetPosition.set(noisePos)
            stateTimer = 8f // search for 8 seconds
        }
    }

    override fun update(
        dt: Float,
        playerPos: Vector3,
        playerHiding: HidingType,
        world: FarmhouseWorld,
        audio: HorrorAudioEngine,
        onPlayerAttacked: (damage: Int, message: String) -> Unit
    ) {
        if (!isActive) return

        attackCooldown = max(0f, attackCooldown - dt)
        isPlayerInSight = canSeePlayer(playerPos, playerHiding, world, viewDistance = 14f)

        // Handle Active Attack Animation Sequence
        if (isAttacking) {
            attackAnimTimer += dt
            // Keep facing player during windup and strike
            val dx = playerPos.x - position.x
            val dz = playerPos.z - position.z
            rotationY = (atan2(dx.toDouble(), dz.toDouble()) * 180.0 / Math.PI).toFloat()

            // Strike impact point: 0.55 seconds into the attack
            if (attackAnimTimer >= 0.55f && !hasDealtDamage) {
                hasDealtDamage = true
                val dist = position.distanceTo(playerPos)
                if (dist < 2.0f && playerHiding == HidingType.NONE) {
                    audio.playClubAttack()
                    onPlayerAttacked(1, "Granny knocked you out with her club!")
                }
            }

            // Total attack animation complete (recovery)
            if (attackAnimTimer >= 1.25f) {
                isAttacking = false
                attackAnimTimer = 0f
                attackCooldown = 2.4f
                state = EnemyState.SEARCH
                stateTimer = 3.5f
                targetPosition.set(playerPos)
            }
            return
        }

        when (state) {
            EnemyState.PATROL -> {
                if (isPlayerInSight) {
                    state = EnemyState.CHASE
                    lastSeenPlayerPos.set(playerPos)
                    audio.playDoorCreak() // Alert sound
                } else {
                    // Pick next waypoint
                    if (world.waypoints.isNotEmpty()) {
                        val wp = world.waypoints[currentWaypointIndex % world.waypoints.size]
                        if (moveTowards(wp, moveSpeed, dt, world)) {
                            currentWaypointIndex = (currentWaypointIndex + 1) % world.waypoints.size
                        }
                    }
                }
            }

            EnemyState.INVESTIGATE_NOISE -> {
                if (isPlayerInSight) {
                    state = EnemyState.CHASE
                    lastSeenPlayerPos.set(playerPos)
                } else {
                    val reached = moveTowards(targetPosition, moveSpeed * 1.2f, dt, world)
                    if (reached) {
                        stateTimer -= dt
                        if (stateTimer <= 0f) {
                            state = EnemyState.PATROL
                        }
                    }
                }
            }

            EnemyState.CHASE -> {
                if (isPlayerInSight) {
                    lastSeenPlayerPos.set(playerPos)
                    moveTowards(playerPos, chaseSpeed, dt, world)

                    // Initiate smooth melee club attack when in range
                    val dist = position.distanceTo(playerPos)
                    if (dist < 1.7f && attackCooldown <= 0f) {
                        isAttacking = true
                        attackAnimTimer = 0f
                        hasDealtDamage = false
                    }
                } else {
                    // Lost sight, navigate to last seen spot
                    state = EnemyState.SEARCH
                    stateTimer = 6.5f
                    targetPosition.set(lastSeenPlayerPos)
                }
            }

            EnemyState.SEARCH -> {
                if (isPlayerInSight) {
                    state = EnemyState.CHASE
                } else {
                    moveTowards(targetPosition, moveSpeed, dt, world)
                    stateTimer -= dt
                    if (stateTimer <= 0f) {
                        state = EnemyState.PATROL
                    }
                }
            }

            else -> state = EnemyState.PATROL
        }
    }
}

/**
 * Grandpa AI:
 * Patrols -> Sees player -> Smooth Aim -> Shoots shotgun with recoil & muzzle flash -> Searches -> Patrols.
 * Does NOT investigate dropped object noise!
 */
class GrandpaAI(startPos: Vector3) : BaseEnemy("Grandpa", startPos, moveSpeed = 1.9f, chaseSpeed = 2.8f) {

    var isAiming = false
    var aimProgress = 0f
    var isShooting = false
    var shotTimer = 0f
    var muzzleFlashActive = false
    var recoilProgress = 0f

    private var aimTime = 0f
    private var shootCooldown = 0f

    override fun update(
        dt: Float,
        playerPos: Vector3,
        playerHiding: HidingType,
        world: FarmhouseWorld,
        audio: HorrorAudioEngine,
        onPlayerAttacked: (damage: Int, message: String) -> Unit
    ) {
        if (!isActive) return

        shootCooldown = max(0f, shootCooldown - dt)
        isPlayerInSight = canSeePlayer(playerPos, playerHiding, world, viewDistance = 16f)

        // Handle Active Shot Animation with smooth Recoil and Muzzle Flash
        if (isShooting) {
            shotTimer += dt
            muzzleFlashActive = shotTimer < 0.12f

            // Recoil kickback and return curve
            recoilProgress = if (shotTimer < 0.15f) {
                shotTimer / 0.15f // kickback
            } else {
                (1.0f - (shotTimer - 0.15f) / 0.50f).coerceIn(0f, 1f) // return
            }

            if (shotTimer >= 0.65f) {
                isShooting = false
                muzzleFlashActive = false
                recoilProgress = 0f
                state = EnemyState.SEARCH
                stateTimer = 4.5f
                targetPosition.set(lastSeenPlayerPos)
            }
            return
        }

        when (state) {
            EnemyState.PATROL -> {
                isAiming = false
                aimProgress = max(0f, aimProgress - dt * 2.0f)
                if (isPlayerInSight) {
                    state = EnemyState.AIM
                    aimTime = 1.25f // smooth aiming duration
                    aimProgress = 0f
                    isAiming = true
                    lastSeenPlayerPos.set(playerPos)
                } else {
                    if (world.waypoints.isNotEmpty()) {
                        val wpIndex = (world.waypoints.size - 1 - (currentWaypointIndex % world.waypoints.size))
                        val wp = world.waypoints[wpIndex]
                        if (moveTowards(wp, moveSpeed, dt, world)) {
                            currentWaypointIndex = (currentWaypointIndex + 1) % world.waypoints.size
                        }
                    }
                }
            }

            EnemyState.AIM -> {
                isAiming = true
                aimProgress = min(1.0f, aimProgress + dt * 1.5f)

                // Face player
                val dx = playerPos.x - position.x
                val dz = playerPos.z - position.z
                rotationY = (atan2(dx.toDouble(), dz.toDouble()) * 180.0 / Math.PI).toFloat()

                aimTime -= dt
                if (aimTime <= 0f) {
                    // Trigger Shotgun Blast
                    isShooting = true
                    shotTimer = 0f
                    muzzleFlashActive = true
                    recoilProgress = 1.0f
                    audio.playShotgunBlast()

                    if (isPlayerInSight && shootCooldown <= 0f && playerHiding == HidingType.NONE) {
                        shootCooldown = 4.0f
                        onPlayerAttacked(1, "Grandpa shot you with his shotgun!")
                    }
                }
            }

            EnemyState.SEARCH -> {
                isAiming = false
                aimProgress = max(0f, aimProgress - dt * 2.0f)
                if (isPlayerInSight && shootCooldown <= 0f) {
                    state = EnemyState.AIM
                    aimTime = 1.1f
                    aimProgress = 0f
                } else {
                    moveTowards(targetPosition, moveSpeed, dt, world)
                    stateTimer -= dt
                    if (stateTimer <= 0f) {
                        state = EnemyState.PATROL
                    }
                }
            }

            else -> state = EnemyState.PATROL
        }
    }
}

/**
 * Slederman AI:
 * Yard & basement wanderer with tentacles. Attacks with hands when in range.
 * Does not investigate object noise.
 */
class SledermanAI(startPos: Vector3) : BaseEnemy("Slederman", startPos, moveSpeed = 2.0f, chaseSpeed = 3.9f) {

    private var attackCooldown = 0f

    override fun update(
        dt: Float,
        playerPos: Vector3,
        playerHiding: HidingType,
        world: FarmhouseWorld,
        audio: HorrorAudioEngine,
        onPlayerAttacked: (damage: Int, message: String) -> Unit
    ) {
        attackCooldown = max(0f, attackCooldown - dt)
        isPlayerInSight = canSeePlayer(playerPos, playerHiding, world, viewDistance = 15f)

        when (state) {
            EnemyState.PATROL -> {
                if (isPlayerInSight) {
                    state = EnemyState.CHASE
                    lastSeenPlayerPos.set(playerPos)
                } else {
                    // Roam exterior yard waypoints
                    val yardNodes = listOf(15, 16, 17, 18, 19).filter { it < world.waypoints.size }
                    if (yardNodes.isNotEmpty()) {
                        val nodeIdx = yardNodes[currentWaypointIndex % yardNodes.size]
                        val wp = world.waypoints[nodeIdx]
                        if (moveTowards(wp, moveSpeed, dt, world)) {
                            currentWaypointIndex = (currentWaypointIndex + 1) % yardNodes.size
                        }
                    }
                }
            }

            EnemyState.CHASE -> {
                if (isPlayerInSight) {
                    lastSeenPlayerPos.set(playerPos)
                    moveTowards(playerPos, chaseSpeed, dt, world)

                    val dist = position.distanceTo(playerPos)
                    if (dist < 1.5f && attackCooldown <= 0f) {
                        attackCooldown = 2.5f
                        audio.playClubAttack()
                        onPlayerAttacked(1, "Slederman attacked you with his tentacles!")
                    }
                } else {
                    state = EnemyState.SEARCH
                    stateTimer = 5.0f
                    targetPosition.set(lastSeenPlayerPos)
                }
            }

            EnemyState.SEARCH -> {
                if (isPlayerInSight) {
                    state = EnemyState.CHASE
                } else {
                    moveTowards(targetPosition, moveSpeed, dt, world)
                    stateTimer -= dt
                    if (stateTimer <= 0f) {
                        state = EnemyState.PATROL
                    }
                }
            }

            else -> state = EnemyState.PATROL
        }
    }
}

/**
 * Angelene special encounter:
 * Chained in basement chest -> freed -> grants key -> attacks for ~60s -> disappears.
 */
class AngeleneAI(startPos: Vector3) : BaseEnemy("Angelene", startPos, moveSpeed = 2.4f, chaseSpeed = 3.6f) {

    var isActiveInWorld = false
    private var attackCooldown = 0f

    override fun update(
        dt: Float,
        playerPos: Vector3,
        playerHiding: HidingType,
        world: FarmhouseWorld,
        audio: HorrorAudioEngine,
        onPlayerAttacked: (damage: Int, message: String) -> Unit
    ) {
        if (!isActiveInWorld) return

        world.angeleneState.activeTimer += dt
        if (world.angeleneState.activeTimer >= 60f) {
            // Disappears after 1 minute
            isActiveInWorld = false
            return
        }

        attackCooldown = max(0f, attackCooldown - dt)
        isPlayerInSight = canSeePlayer(playerPos, playerHiding, world, viewDistance = 12f)

        if (isPlayerInSight) {
            moveTowards(playerPos, chaseSpeed, dt, world)
            if (position.distanceTo(playerPos) < 1.5f && attackCooldown <= 0f) {
                attackCooldown = 2.0f
                audio.playClubAttack()
                onPlayerAttacked(1, "Angelene attacked you!")
            }
        } else {
            // Search or roam around basement
            val basementWps = listOf(11, 12, 13).filter { it < world.waypoints.size }
            if (basementWps.isNotEmpty()) {
                val wp = world.waypoints[basementWps[currentWaypointIndex % basementWps.size]]
                if (moveTowards(wp, moveSpeed, dt, world)) {
                    currentWaypointIndex = (currentWaypointIndex + 1) % basementWps.size
                }
            }
        }
    }
}
