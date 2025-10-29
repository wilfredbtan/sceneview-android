package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Extruded rounded-rectangle box.
 */
class RoundedBox private constructor(
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
    cornerRadius: Float,
    segmentCount: Int
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
        var cornerRadius: Float = DEFAULT_CORNER_RADIUS
            private set
        var segmentCount: Int = DEFAULT_SEGMENT_COUNT
            private set

        fun width(width: Float) = apply { this.width = width }
        fun depth(depth: Float) = apply { this.depth = depth }
        fun height(height: Float) = apply { this.height = height }
        fun cornerRadius(cornerRadius: Float) = apply { this.cornerRadius = cornerRadius }
        fun segmentCount(segmentCount: Int) = apply { this.segmentCount = segmentCount }

        override fun build(engine: Engine): RoundedBox {
            vertices(getVertices(width, depth, height, cornerRadius, segmentCount))
            primitivesIndices(getIndices(segmentCount))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                RoundedBox(
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
                    cornerRadius = cornerRadius,
                    segmentCount = segmentCount
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
    var cornerRadius: Float = cornerRadius
        private set
    var segmentCount: Int = segmentCount
        private set

    /**
     * Rebuild vertex/index data, pushing to GPU via Geometry.update.
     */
    fun update(
        engine: Engine,
        width: Float = this.width,
        depth: Float = this.depth,
        height: Float = this.height,
        cornerRadius: Float = this.cornerRadius,
        segmentCount: Int = this.segmentCount
    ) = apply {
        val newVertices = getVertices(width, depth, height, cornerRadius, segmentCount)
        val newPrimitives =
            if (segmentCount != this.segmentCount) getIndices(segmentCount) else this.primitivesIndices

        super.update(engine = engine, vertices = newVertices, primitivesIndices = newPrimitives)

        this.width = width
        this.depth = depth
        this.height = height
        this.cornerRadius = cornerRadius
        this.segmentCount = segmentCount
    }

    companion object {
        const val DEFAULT_WIDTH = 1.0f
        const val DEFAULT_DEPTH = 1.0f
        const val DEFAULT_HEIGHT = 2.0f
        const val DEFAULT_CORNER_RADIUS = 0f
        const val DEFAULT_SEGMENT_COUNT = 64

        /**
         * Returns vertex list:
         *  - side wall strip (two per arc step)
         *  - bottom cap ring + center
         *  - top cap ring + center
         */
        fun getVertices(
            width: Float,
            depth: Float,
            height: Float,
            cornerRadius: Float,
            segmentCount: Int
        ): List<Vertex> {
            val half = height / 2f
            val clamped = min(cornerRadius, min(width, depth) / 2f)
            val rectW = (width / 2f) - clamped
            val rectD = (depth / 2f) - clamped
            val step = PI / 2.0 / segmentCount

            val bottomCap = mutableListOf<Vertex>()
            val topCap = mutableListOf<Vertex>()
            val side = mutableListOf<Vertex>()

            for (corner in 0 until 4) {
                val baseAngle = corner * (PI / 2.0)
                val baseX = if (corner == 0 || corner == 3) rectW else -rectW
                val baseZ = if (corner < 2) -rectD else rectD

                for (i in 0..segmentCount) {
                    val a = -(baseAngle + i * step)
                    val x = baseX + clamped * cos(a).toFloat()
                    val z = baseZ + clamped * sin(a).toFloat()

                    bottomCap += Vertex(
                        position = Position(x, half, z),
                        normal = Direction(0f, 1f, 0f)
                    )
                    topCap += Vertex(
                        position = Position(x, -half, z),
                        normal = Direction(0f, -1f, 0f)
                    )

                    val n = normalize(Direction(x, 0f, z))
                    side += Vertex(position = Position(x, half, z), normal = n)
                    side += Vertex(position = Position(x, -half, z), normal = n)
                }
            }

            bottomCap += Vertex(position = Position(0f, half, 0f), normal = Direction(0f, 1f, 0f))
            topCap += Vertex(position = Position(0f, -half, 0f), normal = Direction(0f, -1f, 0f))

            return side + bottomCap + topCap
        }

        /**
         * Triangles for sides and caps.
         */
        fun getIndices(segmentCount: Int): List<List<Int>> {
            val out = mutableListOf<List<Int>>()
            val edgeCount = 8 * (segmentCount + 1)
            val capCount = 4 * (segmentCount + 1) + 1

            val bottomOffset = edgeCount
            val topOffset = bottomOffset + capCount

            // Sides
            for (i in 0 until edgeCount - 2 step 4) {
                val next = (i + 4) % edgeCount
                out += listOf(i, i + 1, next + 1)
                out += listOf(i, next + 1, next)
            }

            // Bottom cap fan
            val bottomCenter = bottomOffset + capCount - 1
            for (i in 0 until capCount - 1) {
                val n = (i + 1) % (capCount - 1)
                out += listOf(bottomCenter, bottomOffset + i, bottomOffset + n)
            }

            // Top cap fan
            val topCenter = topOffset + capCount - 1
            for (i in 0 until capCount - 1) {
                val n = (i + 1) % (capCount - 1)
                out += listOf(topCenter, topOffset + i, topOffset + n)
            }

            return out
        }
    }
}
