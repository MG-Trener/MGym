package om.mgtrener.mgym

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import om.mgtrener.mgym.ui.GymApp
import om.mgtrener.mgym.ui.GymViewModel
import om.mgtrener.mgym.ui.theme.MGymTheme
import om.mgtrener.mgym.services.BackgroundUpdates

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(om.mgtrener.mgym.i18n.Language.wrap(base)) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) BackgroundUpdates.schedule(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        om.mgtrener.mgym.i18n.Language.load(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val model = ViewModelProvider(this)[GymViewModel::class.java]
        setContent { MGymTheme { GymApp(model) } }
        if (!BuildConfig.APPLICATION_ID.endsWith(".uitest") && BackgroundUpdates.enabled(this)) {
            if (Build.VERSION.SDK_INT >= 33 && !BackgroundUpdates.canNotify(this))
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else BackgroundUpdates.schedule(this)
        }
    }
}
