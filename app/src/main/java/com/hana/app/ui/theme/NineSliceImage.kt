package com.hana.app.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File

@Composable
fun NineSliceImage(path: String, fixedEdgePercent: Int, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    } ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier) {
        val sourceEdgeX = (image.width * fixedEdgePercent.coerceIn(8, 42) / 100f).toInt().coerceAtLeast(1)
        val sourceEdgeY = (image.height * fixedEdgePercent.coerceIn(8, 42) / 100f).toInt().coerceAtLeast(1)
        val destinationEdgeX = minOf(sourceEdgeX.toFloat(), size.width / 2f).toInt()
        val destinationEdgeY = minOf(sourceEdgeY.toFloat(), size.height / 2f).toInt()
        val sourceX = intArrayOf(0, sourceEdgeX, image.width - sourceEdgeX, image.width)
        val sourceY = intArrayOf(0, sourceEdgeY, image.height - sourceEdgeY, image.height)
        val destinationX = intArrayOf(0, destinationEdgeX, size.width.toInt() - destinationEdgeX, size.width.toInt())
        val destinationY = intArrayOf(0, destinationEdgeY, size.height.toInt() - destinationEdgeY, size.height.toInt())
        for (row in 0..2) for (column in 0..2) {
            drawImage(
                image = image,
                srcOffset = IntOffset(sourceX[column], sourceY[row]),
                srcSize = IntSize(sourceX[column + 1] - sourceX[column], sourceY[row + 1] - sourceY[row]),
                dstOffset = IntOffset(destinationX[column], destinationY[row]),
                dstSize = IntSize((destinationX[column + 1] - destinationX[column]).coerceAtLeast(1), (destinationY[row + 1] - destinationY[row]).coerceAtLeast(1))
            )
        }
    }
}
