package com.example.engine

import kotlin.math.*

/**
 * 3D Vector with comprehensive vector operations.
 */
data class Vector3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    fun set(nx: Float, ny: Float, nz: Float): Vector3 {
        x = nx
        y = ny
        z = nz
        return this
    }

    fun set(other: Vector3): Vector3 {
        x = other.x
        y = other.y
        z = other.z
        return this
    }

    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)

    fun add(ox: Float, oy: Float, oz: Float): Vector3 {
        x += ox
        y += oy
        z += oz
        return this
    }

    fun lengthSquared(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSquared())

    fun distanceTo(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun distanceToSq(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distanceToXZ(other: Vector3): Float {
        val dx = x - other.x
        val dz = z - other.z
        return sqrt(dx * dx + dz * dz)
    }

    fun normalize(): Vector3 {
        val len = length()
        if (len > 0.0001f) {
            x /= len
            y /= len
            z /= len
        }
        return this
    }

    fun normalized(): Vector3 {
        val v = Vector3(x, y, z)
        return v.normalize()
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 {
        return Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }
}

/**
 * Axis-Aligned Bounding Box for fast 3D collision detection and line-of-sight checks.
 */
data class AABB(
    var minX: Float, var minY: Float, var minZ: Float,
    var maxX: Float, var maxY: Float, var maxZ: Float
) {
    fun contains(x: Float, y: Float, z: Float): Boolean {
        return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    }

    fun intersects(other: AABB): Boolean {
        return (minX <= other.maxX && maxX >= other.minX) &&
                (minY <= other.maxY && maxY >= other.minY) &&
                (minZ <= other.maxZ && maxZ >= other.minZ)
    }

    fun intersectsSphere(cx: Float, cy: Float, cz: Float, radius: Float): Boolean {
        val closestX = cx.coerceIn(minX, maxX)
        val closestY = cy.coerceIn(minY, maxY)
        val closestZ = cz.coerceIn(minZ, maxZ)
        val dx = cx - closestX
        val dy = cy - closestY
        val dz = cz - closestZ
        return (dx * dx + dy * dy + dz * dz) <= (radius * radius)
    }

    fun rayIntersects(rayOrigin: Vector3, rayDir: Vector3): Boolean {
        var tmin = (minX - rayOrigin.x) / if (rayDir.x != 0f) rayDir.x else 0.00001f
        var tmax = (maxX - rayOrigin.x) / if (rayDir.x != 0f) rayDir.x else 0.00001f
        if (tmin > tmax) { val tmp = tmin; tmin = tmax; tmax = tmp }

        var tymin = (minY - rayOrigin.y) / if (rayDir.y != 0f) rayDir.y else 0.00001f
        var tymax = (maxY - rayOrigin.y) / if (rayDir.y != 0f) rayDir.y else 0.00001f
        if (tymin > tymax) { val tmp = tymin; tymin = tymax; tymax = tmp }

        if (tmin > tymax || tymin > tmax) return false
        if (tymin > tmin) tmin = tymin
        if (tymax < tmax) tmax = tymax

        var tzmin = (minZ - rayOrigin.z) / if (rayDir.z != 0f) rayDir.z else 0.00001f
        var tzmax = (maxZ - rayOrigin.z) / if (rayDir.z != 0f) rayDir.z else 0.00001f
        if (tzmin > tzmax) { val tmp = tzmin; tzmin = tzmax; tzmax = tmp }

        if (tmin > tzmax || tzmin > tmax) return false
        return true
    }
}

/**
 * 4x4 Matrix for OpenGL ES 2.0 transformations.
 */
class Matrix4 {
    val values = FloatArray(16)

    init {
        setIdentity()
    }

    fun setIdentity(): Matrix4 {
        for (i in 0..15) values[i] = 0f
        values[0] = 1f
        values[5] = 1f
        values[10] = 1f
        values[15] = 1f
        return this
    }

    fun set(src: FloatArray): Matrix4 {
        System.arraycopy(src, 0, values, 0, 16)
        return this
    }

    fun set(other: Matrix4): Matrix4 {
        System.arraycopy(other.values, 0, values, 0, 16)
        return this
    }

    fun setPerspective(fovyDegrees: Float, aspect: Float, near: Float, far: Float): Matrix4 {
        android.opengl.Matrix.perspectiveM(values, 0, fovyDegrees, aspect, near, far)
        return this
    }

    fun setLookAt(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ): Matrix4 {
        android.opengl.Matrix.setLookAtM(values, 0, eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
        return this
    }

    fun translate(x: Float, y: Float, z: Float): Matrix4 {
        android.opengl.Matrix.translateM(values, 0, x, y, z)
        return this
    }

    fun rotate(angleDegrees: Float, x: Float, y: Float, z: Float): Matrix4 {
        android.opengl.Matrix.rotateM(values, 0, angleDegrees, x, y, z)
        return this
    }

    fun scale(sx: Float, sy: Float, sz: Float): Matrix4 {
        android.opengl.Matrix.scaleM(values, 0, sx, sy, sz)
        return this
    }

    fun multiply(lhs: Matrix4, rhs: Matrix4): Matrix4 {
        android.opengl.Matrix.multiplyMM(values, 0, lhs.values, 0, rhs.values, 0)
        return this
    }

    fun copy(): Matrix4 {
        val m = Matrix4()
        m.set(this)
        return m
    }
}
