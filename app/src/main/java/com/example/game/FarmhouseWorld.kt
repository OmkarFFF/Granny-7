package com.example.game

import com.example.engine.AABB
import com.example.engine.Vector3

/**
 * Authoritative 3D Farmhouse environment geometry, room boundaries, doors,
 * furniture, items, hiding spots, and AI navigation nodes.
 * Accurately models Reference Image 6 (Exterior) and Reference Image 7 (Ground Floor, First Floor, Basement).
 */
class FarmhouseWorld {

    val collisionBoxes = ArrayList<AABB>()
    val doors = ArrayList<InteractiveDoor>()
    val items = ArrayList<WorldItem>()
    val hidingSpots = ArrayList<HidingSpot>()
    val waypoints = ArrayList<Vector3>()

    val vehicle = EscapeVehicle()
    val angeleneState = AngeleneEventState()
    val sledrinaState = SledrinaEventState()

    init {
        buildArchitecture()
        spawnItems()
        setupHidingSpots()
        setupDoors()
        setupWaypoints()
    }

    private fun buildArchitecture() {
        collisionBoxes.clear()

        // 1. Exterior Perimeter Fences (yard boundary)
        // Yard extends from X = -25 to +25, Z = -20 to +28
        collisionBoxes.add(AABB(-26f, 0f, -21f, 26f, 2f, -20f)) // North fence
        collisionBoxes.add(AABB(-26f, 0f, -21f, -25f, 2f, 29f)) // West fence
        collisionBoxes.add(AABB(25f, 0f, -21f, 26f, 2f, 29f))  // East fence

        // South fence with Main Escape Gate opening at X = -2.5 to +2.5, Z = 27.5 to 28.5
        collisionBoxes.add(AABB(-26f, 0f, 27.5f, -2.5f, 2f, 28.5f)) // South-West fence
        collisionBoxes.add(AABB(2.5f, 0f, 27.5f, 26f, 2f, 28.5f))   // South-East fence

        // 2. Shed in Yard (X = -14, Z = 14)
        collisionBoxes.add(AABB(-16.5f, 0f, 11.5f, -11.5f, 3.5f, 16.5f))

        // 3. Water Well in Front Yard (X = -8, Z = 16)
        collisionBoxes.add(AABB(-9.2f, 0f, 14.8f, -6.8f, 2.8f, 17.2f))

        // 4. Escape Vehicle Body (X = 8.5, Z = 12)
        collisionBoxes.add(AABB(7.2f, 0f, 9.8f, 9.8f, 1.8f, 14.2f))

        // 5. Farmhouse Outer Walls (Ground Floor: Y = 0 to 3.5)
        // House spans X = -10 to +10, Z = -10 to +8
        // South front wall (with entrance door gap at X = -1.0 to 1.0)
        collisionBoxes.add(AABB(-10.2f, 0f, 7.8f, -1.2f, 3.5f, 8.2f))
        collisionBoxes.add(AABB(1.2f, 0f, 7.8f, 10.2f, 3.5f, 8.2f))

        // North back wall
        collisionBoxes.add(AABB(-10.2f, 0f, -10.2f, 10.2f, 3.5f, -9.8f))
        // West side wall
        collisionBoxes.add(AABB(-10.2f, 0f, -10.2f, -9.8f, 3.5f, 8.2f))
        // East side wall (Garage outer)
        collisionBoxes.add(AABB(9.8f, 0f, -10.2f, 10.2f, 3.5f, 8.2f))

        // Ground Floor Interior Partition Walls:
        // Living Room / Kitchen divider (X = -3.5, Z = -2 to 8)
        collisionBoxes.add(AABB(-3.6f, 0f, -2f, -3.4f, 3.5f, 3f))
        collisionBoxes.add(AABB(-3.6f, 0f, 4.5f, -3.4f, 3.5f, 8f)) // door gap at Z = 3 to 4.5

        // Kitchen / Dining divider (Z = -2, X = -10 to -3.5)
        collisionBoxes.add(AABB(-10f, 0f, -2.1f, -6.5f, 3.5f, -1.9f)) // door gap at X = -6.5 to -5.0
        collisionBoxes.add(AABB(-5.0f, 0f, -2.1f, -3.5f, 3.5f, -1.9f))

        // Garage Wall divider (X = 4.5, Z = -2 to 8)
        collisionBoxes.add(AABB(4.4f, 0f, -2f, 4.6f, 3.5f, 3.5f))
        collisionBoxes.add(AABB(4.4f, 0f, 5.0f, 4.6f, 3.5f, 8f)) // garage interior door gap

        // Storage / Bathroom divider (Z = 3.0, X = 4.5 to 10)
        collisionBoxes.add(AABB(4.5f, 0f, 2.9f, 7.5f, 3.5f, 3.1f))

        // First Floor Outer & Partition Walls (Y = 3.5 to 7.0)
        collisionBoxes.add(AABB(-10.2f, 3.5f, -10.2f, 10.2f, 7f, -9.8f))
        collisionBoxes.add(AABB(-10.2f, 3.5f, 7.8f, 10.2f, 7f, 8.2f))
        collisionBoxes.add(AABB(-10.2f, 3.5f, -10.2f, -9.8f, 7f, 8.2f))
        collisionBoxes.add(AABB(9.8f, 3.5f, -10.2f, 10.2f, 7f, 8.2f))

        // Bedroom 1 & 2 divider (Z = 0, X = -10 to 0)
        collisionBoxes.add(AABB(-10f, 3.5f, -0.1f, -3.0f, 7f, 0.1f))
        // Library / Study divider (X = 2.0, Z = -10 to 0)
        collisionBoxes.add(AABB(1.9f, 3.5f, -10f, 2.1f, 7f, -3.5f))

        // Basement Walls (Y = -4.0 to 0.0, X = -8 to 8, Z = -8 to 4)
        collisionBoxes.add(AABB(-8.2f, -4f, -8.2f, 8.2f, 0f, -7.8f))
        collisionBoxes.add(AABB(-8.2f, -4f, 3.8f, 8.2f, 0f, 4.2f))
        collisionBoxes.add(AABB(-8.2f, -4f, -8.2f, -7.8f, 0f, 4.2f))
        collisionBoxes.add(AABB(7.8f, -4f, -8.2f, 8.2f, 0f, 4.2f))
        // Basement workshop / secret room partition
        collisionBoxes.add(AABB(-2.1f, -4f, -8f, -1.9f, 0f, 0f))

        // Furniture Solid Collisions:
        // Living room sofa
        collisionBoxes.add(AABB(-1.2f, 0f, 1.8f, 1.2f, 0.9f, 3.0f))
        // Dining table
        collisionBoxes.add(AABB(-7.8f, 0f, -7.2f, -5.2f, 0.9f, -4.8f))
        // Kitchen counters
        collisionBoxes.add(AABB(-9.8f, 0f, 0f, -7.5f, 0.95f, 2.5f))
        // Bedroom 1 Bed
        collisionBoxes.add(AABB(-8.8f, 3.5f, 2.8f, -6.2f, 4.5f, 5.2f))
        // Bedroom 2 Bed
        collisionBoxes.add(AABB(-8.8f, 3.5f, -6.2f, -6.2f, 4.5f, -3.8f))
        // Bedroom 3 Bed
        collisionBoxes.add(AABB(4.8f, 3.5f, -6.2f, 7.4f, 4.5f, -3.8f))
        // Angelene Chained Chest in Basement (X = 1.5, Y = -3.5, Z = -6)
        collisionBoxes.add(AABB(0.8f, -4f, -6.5f, 2.2f, -2.6f, -5.5f))
    }

    private fun spawnItems() {
        items.clear()
        // 1. CAR_KEY: Rewarded by freeing Angelene or hidden in Basement secret box
        items.add(WorldItem("car_key", ItemType.CAR_KEY, Vector3(1.5f, -3.2f, -5.8f)))
        // 2. BATTERY: In the Workshop basement or Garage workbench
        items.add(WorldItem("battery", ItemType.BATTERY, Vector3(7.5f, 0.4f, 1.2f)))
        // 3. FUEL_CAN: In the outdoor wooden Shed
        items.add(WorldItem("fuel_can", ItemType.FUEL_CAN, Vector3(-13.8f, 0.3f, 13.8f)))
        // 4. ENGINE_PART: In Kitchen cabinet / drawer
        items.add(WorldItem("engine_part", ItemType.ENGINE_PART, Vector3(-8.5f, 0.95f, 1.2f)))
        // 5. MECHANICAL_PART: In Bedroom 2 dresser
        items.add(WorldItem("mechanical_part", ItemType.MECHANICAL_PART, Vector3(-7.2f, 4.4f, -5.2f)))
        // 6. BOLT_CUTTERS: In Ground floor Storage Room (needed to cut Angelene's chains & Gate chain!)
        items.add(WorldItem("bolt_cutters", ItemType.BOLT_CUTTERS, Vector3(6.5f, 0.4f, 5.5f)))
        // 7. GATE_KEY: In Library / Study desk drawer
        items.add(WorldItem("gate_key", ItemType.GATE_KEY, Vector3(5.2f, 4.4f, -1.8f)))
        // 8. BEDROOM_KEY: In Living Room mantle / table
        items.add(WorldItem("bedroom_key", ItemType.BEDROOM_KEY, Vector3(0.5f, 0.85f, 0.5f)))
        // 9. BOX_KEY: Found near the Water Well
        items.add(WorldItem("box_key", ItemType.BOX_KEY, Vector3(-8.2f, 0.2f, 15.5f)))
        // 10. FLASHLIGHT: On main entrance table
        items.add(WorldItem("flashlight", ItemType.FLASHLIGHT, Vector3(-0.8f, 0.85f, 5.8f)))
    }

    private fun setupHidingSpots() {
        hidingSpots.clear()
        // Wardrobe 1 in Bedroom 1
        hidingSpots.add(HidingSpot("wardrobe_bed1", HidingType.WARDROBE, Vector3(-4.5f, 3.5f, 6.5f), 1.5f, Vector3(0f, 1.1f, 0f), 180f))
        // Wardrobe 2 in Bedroom 2
        hidingSpots.add(HidingSpot("wardrobe_bed2", HidingType.WARDROBE, Vector3(-4.5f, 3.5f, -8.5f), 1.5f, Vector3(0f, 1.1f, 0f), 0f))
        // Under Bed in Bedroom 3 (crawlspace)
        hidingSpots.add(HidingSpot("under_bed3", HidingType.UNDER_BED, Vector3(6.1f, 3.5f, -5.0f), 1.6f, Vector3(0f, 0.35f, 0f), 90f))
        // Storage cabinet in Ground floor storage room
        hidingSpots.add(HidingSpot("cabinet_storage", HidingType.WARDROBE, Vector3(8.5f, 0f, 6.5f), 1.4f, Vector3(0f, 0.9f, 0f), 270f))
    }

    private fun setupDoors() {
        doors.clear()
        // Main front entrance door
        doors.add(InteractiveDoor("door_front", "Front Porch Door", Vector3(0f, 0f, 8.0f), isOpen = false))
        // Kitchen door
        doors.add(InteractiveDoor("door_kitchen", "Kitchen Door", Vector3(-3.5f, 0f, 3.8f), isOpen = false))
        // Dining room door
        doors.add(InteractiveDoor("door_dining", "Dining Room Door", Vector3(-5.8f, 0f, -2.0f), isOpen = false))
        // Garage door to living room
        doors.add(InteractiveDoor("door_garage", "Garage Door", Vector3(4.5f, 0f, 4.2f), isOpen = false))
        // Basement stairs door
        doors.add(InteractiveDoor("door_basement", "Basement Door", Vector3(2.5f, 0f, -2.0f), isOpen = false))
        // Upstairs Bedroom 1 Door (locked)
        doors.add(InteractiveDoor("door_bed1", "Master Bedroom Door", Vector3(-3.0f, 3.5f, 0f), isOpen = false, isLocked = true, requiredKey = ItemType.BEDROOM_KEY))
        // Upstairs Study Door
        doors.add(InteractiveDoor("door_study", "Study Door", Vector3(2.0f, 3.5f, -2.0f), isOpen = false))
    }

    private fun setupWaypoints() {
        waypoints.clear()
        // Ground floor nodes
        waypoints.add(Vector3(0f, 0f, 6.0f))   // Entrance hall
        waypoints.add(Vector3(0f, 0f, 2.0f))   // Living room center
        waypoints.add(Vector3(-6.5f, 0f, 4.0f)) // Kitchen
        waypoints.add(Vector3(-6.5f, 0f, -5.0f))// Dining room
        waypoints.add(Vector3(6.5f, 0f, 4.0f))  // Storage room
        waypoints.add(Vector3(6.5f, 0f, -2.0f)) // Garage
        // Upstairs nodes (Y = 3.5)
        waypoints.add(Vector3(0f, 3.5f, 2.0f))  // Upstairs landing
        waypoints.add(Vector3(-6.5f, 3.5f, 4.0f)) // Bedroom 1
        waypoints.add(Vector3(-6.5f, 3.5f, -5.0f))// Bedroom 2
        waypoints.add(Vector3(5.5f, 3.5f, -5.0f)) // Bedroom 3
        waypoints.add(Vector3(5.5f, 3.5f, 0.0f))  // Study
        // Basement nodes (Y = -3.5)
        waypoints.add(Vector3(2.0f, -3.5f, -1.0f)) // Basement stairs foot
        waypoints.add(Vector3(-4.0f, -3.5f, -4.0f)) // Workshop
        waypoints.add(Vector3(1.5f, -3.5f, -4.5f))  // Secret chamber near Angelene
        // Yard nodes (Y = 0)
        waypoints.add(Vector3(0f, 0f, 14.0f))   // Porch steps
        waypoints.add(Vector3(-8.0f, 0f, 16.0f))// Well
        waypoints.add(Vector3(-14.0f, 0f, 14.0f)) // Shed
        waypoints.add(Vector3(8.5f, 0f, 12.0f)) // Car
        waypoints.add(Vector3(0f, 0f, 25.0f))   // Main gate
    }

    fun isPositionBlocked(x: Float, y: Float, z: Float, radius: Float = 0.35f): Boolean {
        for (box in collisionBoxes) {
            if (box.intersectsSphere(x, y + 0.8f, z, radius)) {
                return true
            }
        }
        for (door in doors) {
            if (!door.isOpen && door.aabb.intersectsSphere(x, y + 0.8f, z, radius)) {
                return true
            }
        }
        // Main gate collision if not open
        if (!vehicle.isGateFullyOpen()) {
            val gateAABB = AABB(-2.5f, 0f, 27.2f, 2.5f, 3.0f, 28.5f)
            if (gateAABB.intersectsSphere(x, y + 0.8f, z, radius)) {
                return true
            }
        }
        return false
    }

    /**
     * Check line-of-sight between two points. Returns true if NO wall blocks the line.
     */
    fun hasLineOfSight(from: Vector3, to: Vector3): Boolean {
        val dir = to - from
        val dist = dir.length()
        if (dist < 0.1f) return true
        val rayDir = dir.normalized()

        for (box in collisionBoxes) {
            if (box.rayIntersects(from, rayDir)) {
                // Approximate distance check to box center
                val boxCenter = Vector3(
                    (box.minX + box.maxX) * 0.5f,
                    (box.minY + box.maxY) * 0.5f,
                    (box.minZ + box.maxZ) * 0.5f
                )
                if (from.distanceTo(boxCenter) < dist) {
                    return false
                }
            }
        }
        for (door in doors) {
            if (!door.isOpen && door.aabb.rayIntersects(from, rayDir)) {
                if (from.distanceTo(door.position) < dist) {
                    return false
                }
            }
        }
        return true
    }
}
