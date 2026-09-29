package om.mgtrener.mgym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import om.mgtrener.mgym.ui.GymApp
import om.mgtrener.mgym.ui.GymViewModel
import om.mgtrener.mgym.ui.theme.MGymTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val model = ViewModelProvider(this)[GymViewModel::class.java]
        setContent { MGymTheme { GymApp(model) } }
    }
}
