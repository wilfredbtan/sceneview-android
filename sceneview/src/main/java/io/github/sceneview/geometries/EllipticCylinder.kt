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
         * Vertex layout:
         *  - side ring as degenerate strip pairs (bottom, top).
         *  - bottom cap ring + center.
         *  - top   cap ring + center.
         */
        fun getVertices(
            width: Float,
            depth: Float,
            height: Float,
            center: Position,
            sideCount: Int
        ): List<Vertex> {
            val halfHeight = height / 2f
            val thetaInc = TWO_PI / sideCount
            val side = mutableListOf<Vertex>()
            val bottomCap = mutableListOf<Vertex>()
            val topCap = mutableListOf<Vertex>()

            for (i in 0..sideCount) {
                val a = i * thetaInc
                val x = (width / 2f) * cos(a)
                val z = (depth / 2f) * sin(a)

                val pBottom = Position(center.x + x, center.y - halfHeight, center.z + z)
                val pTop = Position(center.x + x, center.y + halfHeight, center.z + z)

                // Normal for ellipse: (x/a^2, 0, z/b^2) normalized
                val n = normalize(Direction(2f * x / width, 0f, 2f * z / depth))

                side += Vertex(position = pBottom, normal = n, uvCoordinate = UvCoordinate(i.toFloat() / sideCount, 0f))
                side += Vertex(position = pTop, normal = n, uvCoordinate = UvCoordinate(i.toFloat() / sideCount, 1f))

                // Caps
                bottomCap += Vertex(
                    position = pBottom,
                    normal = Direction(0f, -1f, 0f),
                    uvCoordinate = UvCoordinate((cos(a) + 1f) / 2f, (sin(a) + 1f) / 2f)
                )
                topCap += Vertex(
                    position = pTop,
                    normal = Direction(0f, 1f, 0f),
                    uvCoordinate = UvCoordinate((cos(a) + 1f) / 2f, (sin(a) + 1f) / 2f)
                )
            }

            // Centers
            bottomCap += Vertex(
                position = Position(center.x, center.y - halfHeight, center.z),
                normal = Direction(0f, -1f, 0f),
                uvCoordinate = UvCoordinate(0.5f, 0.5f)
            )
            topCap += Vertex(
                position = Position(center.x, center.y + halfHeight, center.z),
                normal = Direction(0f, 1f, 0f),
                uvCoordinate = UvCoordinate(0.5f, 0.5f)
            )

            return side + bottomCap + topCap
        }

        /**
         * Index groups for sides + bottom cap + top cap.
         */
        fun getIndices(sideCount: Int): List<List<Int>> {
            val out = mutableListOf<List<Int>>()
            val lowerCapOffset = (sideCount + 1) * 2
            val upperCapOffset = lowerCapOffset + sideCount + 1

            // Sides (two triangles per quad)
            for (i in 0 until sideCount) {
                val bl = i * 2
                val br = bl + 2
                val tl = bl + 1
                val tr = br + 1
                out += listOf(bl, tr, br)
                out += listOf(bl, tl, tr)
            }

            // Bottom cap (fan)
            val bottomCenter = lowerCapOffset + sideCount
            for (i in 0 until sideCount) {
                val next = (i + 1) % sideCount
                out += listOf(bottomCenter, lowerCapOffset + i, lowerCapOffset + next)
            }

            // Top cap (fan)
            val topCenter = upperCapOffset + sideCount
            for (i in 0 until sideCount) {
                val next = (i + 1) % sideCount
                out += listOf(topCenter, upperCapOffset + next, upperCapOffset + i)
            }

            return out
        }
    }
}
