package om.mgtrener.mgym.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import om.mgtrener.mgym.i18n.Language

@Composable
fun LocalizedText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, style: TextStyle = LocalTextStyle.current) {
    androidx.compose.material3.Text(Language.text(text),modifier=modifier,color=color,fontSize=fontSize,
        fontWeight=fontWeight,textAlign=textAlign,maxLines=maxLines,overflow=overflow,lineHeight=lineHeight,style=style)
}
