package com.example.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Encapsulates a 3D geometry mesh backed by native FloatBuffers for OpenGL ES 2.0.
 */
class Mesh(
    val vertexCount: Int,
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val colorBuffer: FloatBuffer
) {
    fun render(shader: HorrorShader) {
        if (vertexCount == 0) return

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(shader.aPositionLoc)
        GLES20.glVertexAttribPointer(shader.aPositionLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        normalBuffer.position(0)
        GLES20.glEnableVertexAttribArray(shader.aNormalLoc)
        GLES20.glVertexAttribPointer(shader.aNormalLoc, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

        colorBuffer.position(0)
        GLES20.glEnableVertexAttribArray(shader.aColorLoc)
        GLES20.glVertexAttribPointer(shader.aColorLoc, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(shader.aPositionLoc)
        GLES20.glDisableVertexAttribArray(shader.aNormalLoc)
        GLES20.glDisableVertexAttribArray(shader.aColorLoc)
    }
}

/**
 * High-performance primitive float array list to eliminate boxing and allocation overhead.
 */
class FloatList(initialCapacity: Int = 4096) {
    var data = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size >= data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[size++] = v
    }

    fun clear() {
        size = 0
    }
}

/**
 * Dynamic mesh builder to assemble complex 3D models with vertex positions, normals, and colors.
 */
class MeshBuilder {
    private val vertices = FloatList(4096)
    private val normals = FloatList(4096)
    private val colors = FloatList(4096)

    fun clear() {
        vertices.clear()
        normals.clear()
        colors.clear()
    }

    fun addVertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float, a: Float = 1f) {
        vertices.add(x); vertices.add(y); vertices.add(z)
        normals.add(nx); normals.add(ny); normals.add(nz)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
    }

    fun addQuad(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float, a: Float = 1f
    ) {
        // Triangle 1: 1, 2, 3
        addVertex(x1, y1, z1, nx, ny, nz, r, g, b, a)
        addVertex(x2, y2, z2, nx, ny, nz, r, g, b, a)
        addVertex(x3, y3, z3, nx, ny, nz, r, g, b, a)

        // Triangle 2: 1, 3, 4
        addVertex(x1, y1, z1, nx, ny, nz, r, g, b, a)
        addVertex(x3, y3, z3, nx, ny, nz, r, g, b, a)
        addVertex(x4, y4, z4, nx, ny, nz, r, g, b, a)
    }

    fun addQuad(
        v1: FloatArray, v2: FloatArray, v3: FloatArray, v4: FloatArray,
        normal: FloatArray,
        color: FloatArray
    ) {
        val a = color.getOrElse(3) { 1f }
        addQuad(
            v1[0], v1[1], v1[2],
            v2[0], v2[1], v2[2],
            v3[0], v3[1], v3[2],
            v4[0], v4[1], v4[2],
            normal[0], normal[1], normal[2],
            color[0], color[1], color[2], a
        )
    }

    fun addBox(
        cx: Float, cy: Float, cz: Float,
        sizeX: Float, sizeY: Float, sizeZ: Float,
        r: Float, g: Float, b: Float, a: Float = 1f
    ) {
        val hx = sizeX * 0.5f
        val hy = sizeY * 0.5f
        val hz = sizeZ * 0.5f

        val x0 = cx - hx; val x1 = cx + hx
        val y0 = cy - hy; val y1 = cy + hy
        val z0 = cz - hz; val z1 = cz + hz

        // Front face (Z+)
        addQuad(x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1,  0f, 0f, 1f, r, g, b, a)

        // Back face (Z-)
        addQuad(x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0,  0f, 0f, -1f, r, g, b, a)

        // Top face (Y+)
        addQuad(x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0,  0f, 1f, 0f, r, g, b, a)

        // Bottom face (Y-)
        addQuad(x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1,  0f, -1f, 0f, r, g, b, a)

        // Right face (X+)
        addQuad(x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1,  1f, 0f, 0f, r, g, b, a)

        // Left face (X-)
        addQuad(x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0,  -1f, 0f, 0f, r, g, b, a)
    }

    fun addCylinder(
        cx: Float, cy: Float, cz: Float,
        radius: Float, height: Float, segments: Int = 12,
        r: Float, g: Float, b: Float, a: Float = 1f
    ) {
        val halfH = height * 0.5f
        val angleStep = (2 * Math.PI / segments).toFloat()

        for (i in 0 until segments) {
            val a1 = i * angleStep
            val a2 = (i + 1) * angleStep
            val cos1 = cos(a1.toDouble()).toFloat()
            val sin1 = sin(a1.toDouble()).toFloat()
            val cos2 = cos(a2.toDouble()).toFloat()
            val sin2 = sin(a2.toDouble()).toFloat()

            val x1 = cx + cos1 * radius
            val z1 = cz + sin1 * radius
            val x2 = cx + cos2 * radius
            val z2 = cz + sin2 * radius

            // Side quad
            val nx = (cos1 + cos2) * 0.5f
            val nz = (sin1 + sin2) * 0.5f
            addQuad(
                x1, cy - halfH, z1,
                x2, cy - halfH, z2,
                x2, cy + halfH, z2,
                x1, cy + halfH, z1,
                nx, 0f, nz,
                r, g, b, a
            )

            // Top triangle
            addVertex(cx, cy + halfH, cz, 0f, 1f, 0f, r, g, b, a)
            addVertex(x1, cy + halfH, z1, 0f, 1f, 0f, r, g, b, a)
            addVertex(x2, cy + halfH, z2, 0f, 1f, 0f, r, g, b, a)

            // Bottom triangle
            addVertex(cx, cy - halfH, cz, 0f, -1f, 0f, r, g, b, a)
            addVertex(x2, cy - halfH, z2, 0f, -1f, 0f, r, g, b, a)
            addVertex(x1, cy - halfH, z1, 0f, -1f, 0f, r, g, b, a)
        }
    }

    fun build(): Mesh {
        val count = vertices.size / 3
        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val nBuf = ByteBuffer.allocateDirect(normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val cBuf = ByteBuffer.allocateDirect(colors.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        vBuf.put(vertices.data, 0, vertices.size)
        nBuf.put(normals.data, 0, normals.size)
        cBuf.put(colors.data, 0, colors.size)

        vBuf.position(0)
        nBuf.position(0)
        cBuf.position(0)

        return Mesh(count, vBuf, nBuf, cBuf)
    }
}

/**
 * Generates custom 3D models for the entire Granny 7 game with thread-safe caching.
 */
object MeshFactory {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Mesh>()

    private inline fun getOrCreate(key: String, crossinline creator: () -> Mesh): Mesh {
        return cache.computeIfAbsent(key) { creator() }
    }

    fun preload() {
        createCube()
        createDoorMesh()
        createTableMesh()
        createBedMesh()
        createWardrobeMesh()
        createBookshelfMesh()
        createKitchenCounterMesh()
        createBathtubMesh()
        createVehicleMesh()
        createWellMesh()
        createFenceMesh()
        createGateMesh()
        createTreeMesh()
        createChainedChestMesh()
        createGrannyMesh()
        createGrandpaMesh()
        createSledermanMesh()
        createSledrinaMesh()
        createAngeleneMesh()
        for (i in 0..6) {
            createItemMesh(i)
        }
    }

    // Simple Unit Cube
    fun createCube(): Mesh = getOrCreate("cube") {
        val b = MeshBuilder()
        b.addBox(0f, 0f, 0f, 1f, 1f, 1f, 0.8f, 0.8f, 0.8f)
        b.build()
    }

    // Granny 3D character mesh based on uploaded Image 1
    // Features: iconic white wrinkled gown, gray hair, pale eerie face, red bloody club in right hand
    fun createGrannyMesh(): Mesh = getOrCreate("granny") {
        val b = MeshBuilder()
        // Head
        b.addBox(0f, 1.75f, 0f, 0.32f, 0.35f, 0.32f, 0.72f, 0.68f, 0.65f)
        // Gray wild hair
        b.addBox(0f, 1.95f, -0.05f, 0.35f, 0.15f, 0.36f, 0.45f, 0.45f, 0.45f)
        // Eyes (hollow dark with white pinpoints)
        b.addBox(-0.08f, 1.82f, 0.165f, 0.06f, 0.05f, 0.02f, 0.95f, 0.95f, 0.95f)
        b.addBox(0.08f, 1.82f, 0.165f, 0.06f, 0.05f, 0.02f, 0.95f, 0.95f, 0.95f)
        // Gaping creepy mouth
        b.addBox(0f, 1.68f, 0.165f, 0.14f, 0.08f, 0.02f, 0.15f, 0.05f, 0.05f)

        // Iconic long white gown (cone/box layers from neck down to ankles)
        b.addBox(0f, 1.45f, 0f, 0.55f, 0.35f, 0.42f, 0.88f, 0.88f, 0.90f)
        b.addBox(0f, 1.05f, 0f, 0.65f, 0.55f, 0.50f, 0.82f, 0.82f, 0.84f)
        b.addBox(0f, 0.45f, 0f, 0.76f, 0.75f, 0.60f, 0.76f, 0.76f, 0.78f) // dirt/decay on hem

        // Left arm (relaxed/menacing)
        b.addBox(-0.38f, 1.15f, 0.05f, 0.12f, 0.65f, 0.14f, 0.82f, 0.82f, 0.84f)
        b.addBox(-0.38f, 0.75f, 0.08f, 0.10f, 0.20f, 0.10f, 0.65f, 0.60f, 0.58f) // hand

        // Right arm (raised forward holding the deadly club)
        b.addBox(0.38f, 1.25f, 0.22f, 0.12f, 0.45f, 0.35f, 0.82f, 0.82f, 0.84f)
        b.addBox(0.38f, 1.15f, 0.42f, 0.11f, 0.12f, 0.14f, 0.65f, 0.60f, 0.58f) // hand

        // The Danda / Club (wooden bat with blood splatter at the head)
        b.addCylinder(0.38f, 1.22f, 0.52f, 0.045f, 0.75f, 8, 0.45f, 0.25f, 0.12f)
        // Blood-soaked tip
        b.addCylinder(0.38f, 1.55f, 0.52f, 0.065f, 0.25f, 8, 0.75f, 0.05f, 0.05f)

        b.build()
    }

    // Grandpa 3D character mesh based on uploaded Image 2
    // Features: dark gray clothes, bald head, white eyes, holding double barrel shotgun
    fun createGrandpaMesh(): Mesh = getOrCreate("grandpa") {
        val b = MeshBuilder()
        // Bald head
        b.addBox(0f, 1.76f, 0f, 0.30f, 0.34f, 0.30f, 0.65f, 0.60f, 0.56f)
        b.addBox(-0.07f, 1.82f, 0.155f, 0.05f, 0.04f, 0.02f, 0.90f, 0.90f, 0.90f)
        b.addBox(0.07f, 1.82f, 0.155f, 0.05f, 0.04f, 0.02f, 0.90f, 0.90f, 0.90f)

        // Dark stained work shirt
        b.addBox(0f, 1.30f, 0f, 0.58f, 0.62f, 0.38f, 0.22f, 0.24f, 0.26f)

        // Dark pants
        b.addBox(-0.16f, 0.50f, 0f, 0.22f, 0.98f, 0.24f, 0.16f, 0.18f, 0.20f)
        b.addBox(0.16f, 0.50f, 0f, 0.22f, 0.98f, 0.24f, 0.16f, 0.18f, 0.20f)

        // Arms holding shotgun forward
        b.addBox(-0.35f, 1.25f, 0.22f, 0.12f, 0.14f, 0.40f, 0.22f, 0.24f, 0.26f)
        b.addBox(0.35f, 1.25f, 0.22f, 0.12f, 0.14f, 0.40f, 0.22f, 0.24f, 0.26f)

        // Shotgun (stock + dual barrels)
        b.addBox(0.05f, 1.22f, 0.35f, 0.08f, 0.12f, 0.35f, 0.40f, 0.20f, 0.10f) // wooden stock
        b.addCylinder(-0.01f, 1.25f, 0.70f, 0.025f, 0.65f, 6, 0.15f, 0.15f, 0.18f) // left barrel
        b.addCylinder(0.04f, 1.25f, 0.70f, 0.025f, 0.65f, 6, 0.15f, 0.15f, 0.18f) // right barrel

        b.build()
    }

    // Slederman 3D character mesh based on uploaded Image 4
    // Tall slender figure in black suit with red tie and black tentacles
    fun createSledermanMesh(): Mesh = getOrCreate("slederman") {
        val b = MeshBuilder()
        // Blank pale white head
        b.addBox(0f, 2.15f, 0f, 0.24f, 0.34f, 0.24f, 0.92f, 0.92f, 0.94f)

        // Tall black suit jacket
        b.addBox(0f, 1.60f, 0f, 0.48f, 0.85f, 0.30f, 0.06f, 0.06f, 0.07f)
        // Red tie
        b.addBox(0f, 1.62f, 0.155f, 0.07f, 0.45f, 0.02f, 0.85f, 0.10f, 0.10f)
        // White collar
        b.addBox(0f, 1.95f, 0.152f, 0.14f, 0.10f, 0.02f, 0.95f, 0.95f, 0.95f)

        // Long thin legs
        b.addBox(-0.14f, 0.60f, 0f, 0.16f, 1.25f, 0.18f, 0.06f, 0.06f, 0.07f)
        b.addBox(0.14f, 0.60f, 0f, 0.16f, 1.25f, 0.18f, 0.06f, 0.06f, 0.07f)

        // Long thin arms
        b.addBox(-0.32f, 1.30f, 0.05f, 0.10f, 1.10f, 0.10f, 0.06f, 0.06f, 0.07f)
        b.addBox(0.32f, 1.30f, 0.05f, 0.10f, 1.10f, 0.10f, 0.06f, 0.06f, 0.07f)

        // 4 Curved dark tentacles projecting from back
        b.addBox(-0.45f, 1.85f, -0.35f, 0.08f, 0.60f, 0.55f, 0.02f, 0.02f, 0.02f)
        b.addBox(0.45f, 1.85f, -0.35f, 0.08f, 0.60f, 0.55f, 0.02f, 0.02f, 0.02f)
        b.addBox(-0.55f, 1.35f, -0.45f, 0.08f, 0.70f, 0.65f, 0.02f, 0.02f, 0.02f)
        b.addBox(0.55f, 1.35f, -0.45f, 0.08f, 0.70f, 0.65f, 0.02f, 0.02f, 0.02f)

        b.build()
    }

    // Sledrina 3D specter mesh based on uploaded Image 3
    // Eerie ghost with flowing dark hair, gaping mouth, bloody eyes, white ethereal shroud
    fun createSledrinaMesh(): Mesh = getOrCreate("sledrina") {
        val b = MeshBuilder()
        // Face (pale white)
        b.addBox(0f, 1.55f, 0f, 0.32f, 0.38f, 0.28f, 0.88f, 0.88f, 0.90f)
        // Long dark flowing black hair draping down
        b.addBox(0f, 1.70f, -0.05f, 0.42f, 0.25f, 0.38f, 0.04f, 0.04f, 0.04f)
        b.addBox(-0.20f, 1.30f, 0.02f, 0.12f, 0.75f, 0.28f, 0.04f, 0.04f, 0.04f)
        b.addBox(0.20f, 1.30f, 0.02f, 0.12f, 0.75f, 0.28f, 0.04f, 0.04f, 0.04f)
        // Black hollow abyss eyes
        b.addBox(-0.08f, 1.62f, 0.145f, 0.07f, 0.08f, 0.02f, 0.02f, 0.02f, 0.02f)
        b.addBox(0.08f, 1.62f, 0.145f, 0.07f, 0.08f, 0.02f, 0.02f, 0.02f, 0.02f)
        // Screaming dark black mouth
        b.addBox(0f, 1.45f, 0.145f, 0.14f, 0.14f, 0.02f, 0.01f, 0.01f, 0.01f)

        // Floating white shroud/gown
        b.addBox(0f, 1.10f, 0f, 0.50f, 0.55f, 0.38f, 0.92f, 0.92f, 0.95f, 0.85f)
        b.addBox(0f, 0.55f, 0f, 0.65f, 0.65f, 0.48f, 0.85f, 0.85f, 0.90f, 0.70f)
        b.addBox(0f, 0.05f, 0f, 0.80f, 0.45f, 0.60f, 0.75f, 0.75f, 0.85f, 0.45f)

        b.build()
    }

    // Angelene 3D mesh based on uploaded Image 5
    // Granny's grandmother: antique ruffled collar, withered visage, pale gown
    fun createAngeleneMesh(): Mesh = getOrCreate("angelene") {
        val b = MeshBuilder()
        // Withered head
        b.addBox(0f, 1.65f, 0f, 0.30f, 0.35f, 0.28f, 0.68f, 0.64f, 0.62f)
        // Wild white-grey ruffled hair
        b.addBox(0f, 1.82f, -0.02f, 0.38f, 0.20f, 0.35f, 0.75f, 0.75f, 0.75f)
        // Hollow mouth
        b.addBox(0f, 1.55f, 0.145f, 0.12f, 0.08f, 0.02f, 0.12f, 0.04f, 0.04f)

        // Ruffled collar & antique shawl
        b.addBox(0f, 1.38f, 0f, 0.58f, 0.24f, 0.42f, 0.78f, 0.74f, 0.70f)
        // Gown
        b.addBox(0f, 0.85f, 0f, 0.62f, 0.85f, 0.48f, 0.70f, 0.68f, 0.66f)
        b.addBox(0f, 0.25f, 0f, 0.72f, 0.45f, 0.56f, 0.62f, 0.60f, 0.58f)

        b.build()
    }

    // Chained Chest where Angelene is trapped in the basement
    fun createChainedChestMesh(): Mesh = getOrCreate("chest") {
        val b = MeshBuilder()
        // Wooden trunk body
        b.addBox(0f, 0.45f, 0f, 1.4f, 0.9f, 0.9f, 0.35f, 0.22f, 0.12f)
        // Metal reinforced corners
        b.addBox(0f, 0.85f, 0f, 1.44f, 0.1f, 0.94f, 0.18f, 0.18f, 0.20f)
        // Chains wrapped across chest
        b.addBox(0f, 0.50f, 0.47f, 0.08f, 0.80f, 0.04f, 0.55f, 0.55f, 0.58f)
        b.addBox(-0.35f, 0.50f, 0.47f, 0.08f, 0.80f, 0.04f, 0.55f, 0.55f, 0.58f)
        b.addBox(0.35f, 0.50f, 0.47f, 0.08f, 0.80f, 0.04f, 0.55f, 0.55f, 0.58f)
        // Heavy brass padlock
        b.addBox(0f, 0.52f, 0.51f, 0.16f, 0.20f, 0.08f, 0.78f, 0.62f, 0.18f)
        b.build()
    }

    // Escape Car Mesh based on Image 6 exterior
    fun createVehicleMesh(): Mesh = getOrCreate("vehicle") {
        val b = MeshBuilder()
        // Car Main Body (rusty vintage sedan)
        val rustR = 0.42f; val rustG = 0.26f; val rustB = 0.18f
        b.addBox(0f, 0.55f, 0f, 1.9f, 0.55f, 3.8f, rustR, rustG, rustB)
        // Cabin & Windows
        b.addBox(0f, 1.15f, -0.2f, 1.6f, 0.65f, 2.0f, rustR * 0.9f, rustG * 0.9f, rustB * 0.9f)
        // Windshield
        b.addBox(0f, 1.15f, 0.82f, 1.45f, 0.55f, 0.05f, 0.18f, 0.22f, 0.26f)
        // Rear window
        b.addBox(0f, 1.15f, -1.22f, 1.45f, 0.55f, 0.05f, 0.18f, 0.22f, 0.26f)
        // Headlights (glass)
        b.addBox(-0.65f, 0.62f, 1.92f, 0.28f, 0.22f, 0.05f, 0.95f, 0.92f, 0.75f)
        b.addBox(0.65f, 0.62f, 1.92f, 0.28f, 0.22f, 0.05f, 0.95f, 0.92f, 0.75f)
        // Front Grille
        b.addBox(0f, 0.55f, 1.92f, 0.75f, 0.32f, 0.04f, 0.15f, 0.15f, 0.15f)
        // Front Bumper
        b.addBox(0f, 0.30f, 1.95f, 1.85f, 0.15f, 0.12f, 0.35f, 0.35f, 0.38f)
        // Wheels
        val tireColor = 0.10f
        b.addCylinder(-0.95f, 0.35f, 1.1f, 0.35f, 0.25f, 10, tireColor, tireColor, tireColor)
        b.addCylinder(0.95f, 0.35f, 1.1f, 0.35f, 0.25f, 10, tireColor, tireColor, tireColor)
        b.addCylinder(-0.95f, 0.35f, -1.1f, 0.35f, 0.25f, 10, tireColor, tireColor, tireColor)
        b.addCylinder(0.95f, 0.35f, -1.1f, 0.35f, 0.25f, 10, tireColor, tireColor, tireColor)

        // Engine Bay inside hood (accessible for battery, engine part, mechanical part, fuel)
        b.addBox(0f, 0.78f, 1.25f, 1.1f, 0.08f, 0.9f, 0.22f, 0.22f, 0.24f)

        b.build()
    }

    // Water Well in front yard
    fun createWellMesh(): Mesh = getOrCreate("well") {
        val b = MeshBuilder()
        // Stone base
        b.addCylinder(0f, 0.65f, 0f, 1.1f, 1.3f, 14, 0.38f, 0.36f, 0.34f)
        // Wooden roof pillars
        b.addBox(-0.95f, 1.85f, 0f, 0.14f, 1.2f, 0.14f, 0.32f, 0.20f, 0.10f)
        b.addBox(0.95f, 1.85f, 0f, 0.14f, 1.2f, 0.14f, 0.32f, 0.20f, 0.10f)
        // Wooden roof
        b.addBox(0f, 2.55f, 0f, 2.3f, 0.25f, 1.8f, 0.26f, 0.16f, 0.08f)
        // Winch crank
        b.addCylinder(0f, 2.05f, 0f, 0.08f, 1.7f, 6, 0.40f, 0.24f, 0.12f)
        b.build()
    }

    // Spooky Forest Tree
    fun createTreeMesh(): Mesh = getOrCreate("tree") {
        val b = MeshBuilder()
        // Dark pine/gnarled trunk
        b.addCylinder(0f, 2.5f, 0f, 0.35f, 5.0f, 8, 0.22f, 0.16f, 0.10f)
        // Dark foliage tiers
        b.addCylinder(0f, 4.5f, 0f, 1.8f, 2.0f, 8, 0.08f, 0.14f, 0.09f)
        b.addCylinder(0f, 6.2f, 0f, 1.4f, 1.8f, 8, 0.07f, 0.12f, 0.08f)
        b.addCylinder(0f, 7.6f, 0f, 0.9f, 1.5f, 8, 0.06f, 0.10f, 0.07f)
        b.build()
    }

    // Wooden Fence section
    fun createFenceMesh(): Mesh = getOrCreate("fence") {
        val b = MeshBuilder()
        val woodR = 0.32f; val woodG = 0.24f; val woodB = 0.16f
        // Horizontal rails
        b.addBox(0f, 0.45f, 0f, 2.8f, 0.10f, 0.08f, woodR, woodG, woodB)
        b.addBox(0f, 1.15f, 0f, 2.8f, 0.10f, 0.08f, woodR, woodG, woodB)
        // Vertical pickets
        for (i in -4..4) {
            b.addBox(i * 0.32f, 0.85f, 0.04f, 0.12f, 1.5f, 0.04f, woodR * 0.95f, woodG * 0.95f, woodB * 0.95f)
        }
        b.build()
    }

    // Main Escape Gate with heavy chained lock
    fun createGateMesh(): Mesh = getOrCreate("gate") {
        val b = MeshBuilder()
        val ironR = 0.18f; val ironG = 0.18f; val ironB = 0.20f
        // Stone Gate Pillars
        b.addBox(-1.9f, 1.4f, 0f, 0.65f, 2.8f, 0.65f, 0.42f, 0.40f, 0.38f)
        b.addBox(1.9f, 1.4f, 0f, 0.65f, 2.8f, 0.65f, 0.42f, 0.40f, 0.38f)
        // Lanterns on top of stone pillars
        b.addBox(-1.9f, 2.95f, 0f, 0.30f, 0.35f, 0.30f, 0.95f, 0.85f, 0.45f)
        b.addBox(1.9f, 2.95f, 0f, 0.30f, 0.35f, 0.30f, 0.95f, 0.85f, 0.45f)

        // Left gate wing
        b.addBox(-0.85f, 1.2f, 0f, 1.45f, 2.1f, 0.10f, ironR, ironG, ironB)
        // Right gate wing
        b.addBox(0.85f, 1.2f, 0f, 1.45f, 2.1f, 0.10f, ironR, ironG, ironB)
        // Center Chain & Lock
        b.addBox(0f, 1.2f, 0.08f, 0.35f, 0.35f, 0.12f, 0.72f, 0.58f, 0.22f)

        b.build()
    }

    // Wardrobe hiding spot (closet with doors that can be opened/peaked through)
    fun createWardrobeMesh(): Mesh = getOrCreate("wardrobe") {
        val b = MeshBuilder()
        val woodR = 0.36f; val woodG = 0.22f; val woodB = 0.12f
        // Outer cabinet
        b.addBox(0f, 1.25f, 0f, 1.3f, 2.4f, 0.75f, woodR, woodG, woodB)
        // Door panels with slats
        b.addBox(-0.32f, 1.25f, 0.38f, 0.58f, 2.2f, 0.04f, woodR * 0.9f, woodG * 0.9f, woodB * 0.9f)
        b.addBox(0.32f, 1.25f, 0.38f, 0.58f, 2.2f, 0.04f, woodR * 0.9f, woodG * 0.9f, woodB * 0.9f)
        // Brass handles
        b.addBox(-0.06f, 1.25f, 0.42f, 0.04f, 0.14f, 0.05f, 0.82f, 0.72f, 0.25f)
        b.addBox(0.06f, 1.25f, 0.42f, 0.04f, 0.14f, 0.05f, 0.82f, 0.72f, 0.25f)
        b.build()
    }

    // Bed with crawlspace underneath for hiding
    fun createBedMesh(): Mesh = getOrCreate("bed") {
        val b = MeshBuilder()
        // Wooden frame
        b.addBox(0f, 0.30f, 0f, 1.6f, 0.20f, 2.2f, 0.38f, 0.24f, 0.14f)
        // 4 Legs (raising bed off floor for crawlspace underneath)
        b.addBox(-0.75f, 0.12f, -1.05f, 0.12f, 0.24f, 0.12f, 0.30f, 0.20f, 0.12f)
        b.addBox(0.75f, 0.12f, -1.05f, 0.12f, 0.24f, 0.12f, 0.30f, 0.20f, 0.12f)
        b.addBox(-0.75f, 0.12f, 1.05f, 0.12f, 0.24f, 0.12f, 0.30f, 0.20f, 0.12f)
        b.addBox(0.75f, 0.12f, 1.05f, 0.12f, 0.24f, 0.12f, 0.30f, 0.20f, 0.12f)
        // Mattress & quilt
        b.addBox(0f, 0.55f, 0.05f, 1.55f, 0.32f, 2.1f, 0.52f, 0.32f, 0.28f)
        // Pillows
        b.addBox(-0.40f, 0.76f, -0.80f, 0.55f, 0.15f, 0.40f, 0.85f, 0.82f, 0.78f)
        b.addBox(0.40f, 0.76f, -0.80f, 0.55f, 0.15f, 0.40f, 0.85f, 0.82f, 0.78f)
        // Headboard
        b.addBox(0f, 0.80f, -1.10f, 1.62f, 0.90f, 0.10f, 0.38f, 0.24f, 0.14f)
        b.build()
    }

    // Interactive wooden door
    fun createDoorMesh(): Mesh = getOrCreate("door") {
        val b = MeshBuilder()
        // Door panel hinged at (0, 0, 0)
        b.addBox(0.45f, 1.15f, 0f, 0.90f, 2.30f, 0.08f, 0.45f, 0.28f, 0.16f)
        // Brass knob
        b.addBox(0.80f, 1.10f, 0.06f, 0.08f, 0.08f, 0.08f, 0.85f, 0.75f, 0.25f)
        b.addBox(0.80f, 1.10f, -0.06f, 0.08f, 0.08f, 0.08f, 0.85f, 0.75f, 0.25f)
        b.build()
    }

    // Wooden table & chair
    fun createTableMesh(): Mesh = getOrCreate("table") {
        val b = MeshBuilder()
        // Table top
        b.addBox(0f, 0.85f, 0f, 1.6f, 0.08f, 1.0f, 0.40f, 0.25f, 0.14f)
        // Legs
        b.addBox(-0.72f, 0.42f, -0.42f, 0.08f, 0.84f, 0.08f, 0.32f, 0.20f, 0.10f)
        b.addBox(0.72f, 0.42f, -0.42f, 0.08f, 0.84f, 0.08f, 0.32f, 0.20f, 0.10f)
        b.addBox(-0.72f, 0.42f, 0.42f, 0.08f, 0.84f, 0.08f, 0.32f, 0.20f, 0.10f)
        b.addBox(0.72f, 0.42f, 0.42f, 0.08f, 0.84f, 0.08f, 0.32f, 0.20f, 0.10f)
        b.build()
    }

    // Bookshelf for Library / Study
    fun createBookshelfMesh(): Mesh = getOrCreate("bookshelf") {
        val b = MeshBuilder()
        // Cabinet frame
        b.addBox(0f, 1.25f, 0f, 1.2f, 2.4f, 0.45f, 0.32f, 0.18f, 0.10f)
        // Colored books on shelves
        b.addBox(0f, 0.50f, 0.05f, 1.05f, 0.35f, 0.25f, 0.52f, 0.20f, 0.20f)
        b.addBox(0f, 1.10f, 0.05f, 1.05f, 0.35f, 0.25f, 0.20f, 0.35f, 0.45f)
        b.addBox(0f, 1.70f, 0.05f, 1.05f, 0.35f, 0.25f, 0.45f, 0.42f, 0.18f)
        b.build()
    }

    // Kitchen Counter & Stove
    fun createKitchenCounterMesh(): Mesh = getOrCreate("kitchen") {
        val b = MeshBuilder()
        // Counter base
        b.addBox(0f, 0.45f, 0f, 2.2f, 0.90f, 0.85f, 0.85f, 0.82f, 0.78f)
        // Metal sink
        b.addBox(-0.55f, 0.92f, 0f, 0.65f, 0.05f, 0.55f, 0.35f, 0.38f, 0.42f)
        // Stove top with burners
        b.addBox(0.55f, 0.92f, 0f, 0.70f, 0.05f, 0.60f, 0.15f, 0.15f, 0.15f)
        b.build()
    }

    // Bathtub for bathroom
    fun createBathtubMesh(): Mesh = getOrCreate("bathtub") {
        val b = MeshBuilder()
        // Porcelain tub
        b.addBox(0f, 0.35f, 0f, 1.5f, 0.65f, 0.80f, 0.82f, 0.84f, 0.86f)
        // Faucet
        b.addBox(0.65f, 0.75f, 0f, 0.10f, 0.18f, 0.08f, 0.75f, 0.75f, 0.80f)
        b.build()
    }

    // 3D Item Meshes (Keys, Battery, Fuel Can, Engine Part, Mechanical Part, Bolt Cutters)
    fun createItemMesh(itemType: Int): Mesh = getOrCreate("item_$itemType") {
        val b = MeshBuilder()
        when (itemType) {
            0 -> { // Car Key / Keys: brass key with head and shaft
                b.addCylinder(0f, 0.02f, 0f, 0.08f, 0.02f, 8, 0.95f, 0.82f, 0.25f)
                b.addBox(0f, 0.02f, 0.12f, 0.04f, 0.02f, 0.18f, 0.95f, 0.82f, 0.25f)
                b.addBox(0.03f, 0.02f, 0.18f, 0.04f, 0.02f, 0.04f, 0.95f, 0.82f, 0.25f)
            }
            1 -> { // Car Battery: black rectangular battery with red & blue terminals
                b.addBox(0f, 0.15f, 0f, 0.32f, 0.25f, 0.22f, 0.12f, 0.12f, 0.14f)
                b.addCylinder(-0.09f, 0.30f, 0f, 0.03f, 0.06f, 6, 0.85f, 0.15f, 0.15f) // positive red
                b.addCylinder(0.09f, 0.30f, 0f, 0.03f, 0.06f, 6, 0.25f, 0.45f, 0.85f) // negative blue
            }
            2 -> { // Fuel Can: red canister with black spout
                b.addBox(0f, 0.22f, 0f, 0.26f, 0.38f, 0.18f, 0.85f, 0.12f, 0.12f)
                b.addCylinder(0.08f, 0.44f, 0f, 0.03f, 0.10f, 6, 0.15f, 0.15f, 0.15f)
                b.addBox(-0.02f, 0.43f, 0f, 0.12f, 0.05f, 0.05f, 0.15f, 0.15f, 0.15f) // handle
            }
            3 -> { // Engine Part / Spark Plugs: metallic cylinder with manifold tubes
                b.addBox(0f, 0.12f, 0f, 0.28f, 0.16f, 0.18f, 0.55f, 0.58f, 0.62f)
                b.addCylinder(0f, 0.24f, 0f, 0.06f, 0.12f, 8, 0.85f, 0.85f, 0.88f)
            }
            4 -> { // Mechanical Part / Fan Belt: cog & rubber belt
                b.addCylinder(0f, 0.06f, 0f, 0.15f, 0.05f, 12, 0.25f, 0.25f, 0.28f)
                b.addCylinder(0f, 0.06f, 0f, 0.05f, 0.06f, 8, 0.82f, 0.65f, 0.25f)
            }
            5 -> { // Bolt Cutters: heavy orange/red handles and silver steel jaws
                b.addBox(-0.07f, 0.04f, -0.15f, 0.04f, 0.04f, 0.35f, 0.85f, 0.35f, 0.10f)
                b.addBox(0.07f, 0.04f, -0.15f, 0.04f, 0.04f, 0.35f, 0.85f, 0.35f, 0.10f)
                b.addBox(0f, 0.04f, 0.10f, 0.12f, 0.05f, 0.15f, 0.75f, 0.75f, 0.80f)
            }
            else -> { // Default generic puzzle item
                b.addBox(0f, 0.10f, 0f, 0.18f, 0.18f, 0.18f, 0.85f, 0.75f, 0.30f)
            }
        }
        b.build()
    }
}
