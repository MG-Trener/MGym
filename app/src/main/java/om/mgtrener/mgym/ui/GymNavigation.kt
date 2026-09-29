package om.mgtrener.mgym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.ui.theme.Lime

@Composable
fun GymNavigation(selected: Int, select: (Int) -> Unit) {
    val labels = listOf("Сегодня", "Календарь", "Прогресс", "Награды", "Настройки")
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
                labels.forEachIndexed { index, label ->
                    val color = if(index == selected) Lime else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f).selectable(index == selected, role = Role.Tab, onClick = { select(index) })
                        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Canvas(Modifier.size(20.dp)) {
                            val u = size.width / 24
                            fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x*u,y*u), Offset(xx*u,yy*u), 1.7f*u, StrokeCap.Round)
                            when(index) {
                                0 -> { line(3f,11f,12f,3f); line(12f,3f,21f,11f); line(6f,10f,6f,21f); line(18f,10f,18f,21f); line(6f,21f,18f,21f) }
                                1 -> { drawRect(color, Offset(4*u,5*u), Size(16*u,16*u), style = Stroke(1.7f*u)); line(4f,10f,20f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f); line(8f,14f,10f,14f); line(14f,17f,16f,17f) }
                                2 -> { line(4f,4f,4f,21f); line(4f,21f,21f,21f); line(7f,16f,12f,11f); line(12f,11f,16f,13f); line(16f,13f,21f,5f) }
                                3 -> { val p=Path().apply { moveTo(12*u,2*u); lineTo(21*u,10*u); lineTo(12*u,22*u); lineTo(3*u,10*u); close() }; drawPath(p,color,style=Stroke(1.7f*u)); line(8f,10f,11f,13f); line(11f,13f,16f,8f) }
                                else -> { line(3f,6f,21f,6f); line(3f,12f,21f,12f); line(3f,18f,21f,18f); drawCircle(color,2.3f*u,Offset(8*u,6*u)); drawCircle(color,2.3f*u,Offset(16*u,12*u)); drawCircle(color,2.3f*u,Offset(10*u,18*u)) }
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}
