package `in`.c1ph3rj.scanly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import `in`.c1ph3rj.scanly.navigation.ScanlyNavHost
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanlyApp()
        }
    }
}

@Composable
private fun ScanlyApp() {
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = themeMode.resolveDarkTheme(systemDark)
    val navController = rememberNavController()
    
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    androidx.compose.runtime.DisposableEffect(isDarkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { isDarkTheme },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { isDarkTheme }
        )
        onDispose {}
    }

    ScanlyTheme(
        darkTheme = isDarkTheme,
    ) {
        ScanlyNavHost(navController = navController)
    }
}


private fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
