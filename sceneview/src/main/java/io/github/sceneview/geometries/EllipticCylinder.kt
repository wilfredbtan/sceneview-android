package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.TWO_PI
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elliptic cylinder with different width/depth radii (elliptic cross-section).
 */
class EllipticCylinder private constructor(
    primitiveType: PrimitiveType,
    vertices: List<Vertex>,
    vertexBuffer: VertexBuffer,
    primitivesIndices: List<List<Int>>, // triangles
    indexBuffer: IndexBuffer,
    primitivesOffsets: List<IntRange>,
    boundingBox: Box,
    width: Float,
    depth: Float,
    height: Float,
    center: Position,
    sideCount: Int
) : Geometry(
    primitiveType = primitiveType,
    vertices = vertices,
    vertexBuffer = vertexBuffer,
    primitivesIndices = primitivesIndices,
    indexBuffer = indexBuffer,
    primitivesOffsets = primitivesOffsets,
    boundingBox = boundingBox
) {

    class Builder : Geometry.Builder(PrimitiveType.TRIANGLES) {
        var width: Float = DEFAULT_WIDTH
            private set
        var depth: Float = DEFAULT_DEPTH
            private set
        var height: Float = DEFAULT_HEIGHT
            private set
        var center: Position = DEFAULT_CENTER
            private set
        var sideCount: Int = DEFAULT_SIDE_COUNT
            private set

        fun width(width: Float) = apply { this.width = width }
        fun depth(depth: Float) = apply { this.depth = depth }
        fun height(height: Float) = apply { this.height = height }
        fun center(center: Position) = apply { this.center = center }
        fun sideCount(sideCount: Int) = apply { this.sideCount = sideCount }

        override fun build(engine: Engine): EllipticCylinder {
            vertices(getVertices(width, depth, height, center, sideCount))
            primitivesIndices(getIndices(sideCount))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                EllipticCylinder(
                    primitiveType = primitiveType,
                    vertices = vertices,
                    vertexBuffer = vertexBuffer,
                    primitivesIndices = indices,
                    indexBuffer = indexBuffer,
                    primitivesOffsets = offsets,
                    boundingBox = boundingBox,
                    width = width,
                    depth = depth,
                    height = height,
                    center = center,
                    sideCount = sideCount
                )
            }
        }
    }

    var width: Float = width
        private set
    var depth: Float = depth
        private set
    var height: Float = height
        private set
    var center: Position = center
        private set
    var sideCount: Int = sideCount
        private set

    /**
     * Rebuilds vertex/index data and pushes changes to GPU buffers.
     */
    fun update(
        engine: Engine,
        width: Float = this.width,
        depth: Float = this.depth,
        height: Float = this.height,
        center: Position = this.center,
        sideCount: Int = this.sideCount
    ) = apply {
        val newVertices = getVertices(width, depth, height, center, sideCount)

        // Recompute indices only when side count changes.
        val newPrimitives =
            if (sideCount != this.sideCount) getIndices(sideCount) else this.primitivesIndices

        super.update(engine = engine, vertices = newVertices, primitivesIndices = newPrimitives)

        this.width = width
        this.depth = depth
        this.height = height
        this.center = center
        this.sideCount = sideCount
    }

    companion object {
        const val DEFAULT_WIDTH = 1.0f
        const val DEFAULT_DEPTH = 1.0f
        const val DEFAULT_HEIGHT = 2.0f
        val DEFAULT_CENTER = Position(0.0f)
        const val DEFAULT_SIDE_COUNT = 64

        /**
         * Generates the vertices for the elliptic cylinder.
         * @param width The width of the cylinder.
         * @param depth The depth of the cylinder.
         * @param height The height of the cylinder.
         * @param center The center position of the cylinder.
         * @param sideCount The number of segments around the cylinder.
         * @return A list of vertices for the geometry.
         */
        fun getVertices(
            width: Float,
            depth: Float,
            height: Float,
            center: Position,
            sideCount: Int
        ): List<Vertex> {
            val half = height / 2f
            val theta = (2.0 * Math.PI / sideCount).toFloat()

            val side = mutableListOf<Vertex>()       // [bottom, top] per step
            val bottomCap = mutableListOf<Vertex>()  // ring at y = -half
            val topCap = mutableListOf<Vertex>()     // ring at y = +half

            for (i in 0..sideCount) {
                val a = i * theta
                val x = (width / 2f) * cos(a)
                val z = (depth / 2f) * sin(a)

                val pB = Position(center.x + x, center.y - half, center.z + z)
                val pT = Position(center.x + x, center.y + half, center.z + z)

                // Ellipse-normal approximation: (x/a^2, 0, z/b^2) normalized
                val n = normalize(Direction(2f * x / width, 0f, 2f * z / depth))

                // Side ring [bottom, top]
                side += Vertex(pB, n, UvCoordinate(i.toFloat() / sideCount, 0f))
                side += Vertex(pT, n, UvCoordinate(i.toFloat() / sideCount, 1f))

                // Caps
                val uCap = (cos(a) + 1f) * 0.5f
                val vCap = (sin(a) + 1f) * 0.5f
                bottomCap += Vertex(pB, Direction(0f, -1f, 0f), UvCoordinate(uCap, vCap))
                topCap += Vertex(pT, Direction(0f, +1f, 0f), UvCoordinate(uCap, vCap))
            }

            // Centers
            bottomCap += Vertex(
                position = Position(center.x, center.y - half, center.z),
                normal = Direction(0f, -1f, 0f),
                uvCoordinate = UvCoordinate(0.5f, 0.5f)
            )
            topCap += Vertex(
                position = Position(center.x, center.y + half, center.z),
                normal = Direction(0f, +1f, 0f),
                uvCoordinate = UvCoordinate(0.5f, 0.5f)
            )

            return side + bottomCap + topCap
        }

        /**
         * Generates the indices for the elliptic cylinder.
         * @param sideCount The number of segments around the cylinder.
         * @return A list of index groups for the geometry.
         */
        fun getIndices(sideCount: Int): List<List<Int>> {
            val out = mutableListOf<List<Int>>()

            val sideVerts = (sideCount + 1) * 2
            val bottomOffset = sideVerts
            val bottomRingCount = sideCount + 1
            val topOffset = bottomOffset + bottomRingCount

            // Sides: quads along the ring (use i..i+1; last step uses the duplicated vertex)
            for (i in 0 until sideCount) {
                val bl = 2 * i + 0
                val tl = 2 * i + 1
                val br = 2 * (i + 1) + 0
                val tr = 2 * (i + 1) + 1
                out += listOf(tl, tr, br)
                out += listOf(tl, br, bl)
            }

            // Bottom cap fan (ring + center)
            val bottomCenter = bottomOffset + bottomRingCount - 1
            for (i in 0 until bottomRingCount - 1) {
                val n = i + 1 // no modulo
                out += listOf(bottomCenter, bottomOffset + i, bottomOffset + n)
            }

            // Top cap fan (ring + center)
            val topCenter = topOffset + bottomRingCount - 1
            for (i in 0 until bottomRingCount - 1) {
                val n = i + 1 // no modulo
                out += listOf(topCenter, topOffset + n, topOffset + i)
            }

            return out
        }

    }
}
