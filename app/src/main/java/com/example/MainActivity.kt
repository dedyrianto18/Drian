package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.NavigationScreen
import com.example.ui.screens.AiCopilotScreen
import com.example.ui.screens.BotDetailScreen
import com.example.ui.screens.BotsScreen
import com.example.ui.screens.CreateBotScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExchangeSettingsScreen
import com.example.ui.screens.KnowledgeScreen
import com.example.ui.theme.CryptoQuantTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check initialization with launch intent before any Firebase AI or UI operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            CryptoQuantTheme {
                CryptoQuantApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CryptoQuantApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    // BackHandler to navigate back to Dashboard when on secondary screens
    if (currentScreen != NavigationScreen.DASHBOARD) {
        BackHandler {
            if (currentScreen == NavigationScreen.BOT_DETAIL || currentScreen == NavigationScreen.CREATE_BOT) {
                viewModel.navigateTo(NavigationScreen.BOTS)
            } else {
                viewModel.navigateTo(NavigationScreen.DASHBOARD)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = NeonCyan,
                tonalElevation = 4.dp,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                val navItems = listOf(
                    NavigationScreen.DASHBOARD to (Icons.Filled.Public to Icons.Outlined.Public),
                    NavigationScreen.BOTS to (Icons.Filled.SmartToy to Icons.Outlined.SmartToy),
                    NavigationScreen.AI_COPILOT to (Icons.Filled.AutoAwesome to Icons.Outlined.AutoAwesome),
                    NavigationScreen.KNOWLEDGE_BASE to (Icons.Filled.School to Icons.Outlined.School),
                    NavigationScreen.EXCHANGE_SETTINGS to (Icons.Filled.Security to Icons.Outlined.Security)
                )

                navItems.forEach { (screen, iconPair) ->
                    val isSelected = currentScreen == screen ||
                        (screen == NavigationScreen.BOTS && (currentScreen == NavigationScreen.CREATE_BOT || currentScreen == NavigationScreen.BOT_DETAIL))

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.navigateTo(screen) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) iconPair.first else iconPair.second,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                    )
                }
            }
        },
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBg)
        ) {
            when (currentScreen) {
                NavigationScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                NavigationScreen.BOTS -> BotsScreen(viewModel = viewModel)
                NavigationScreen.CREATE_BOT -> CreateBotScreen(viewModel = viewModel)
                NavigationScreen.BOT_DETAIL -> BotDetailScreen(viewModel = viewModel)
                NavigationScreen.AI_COPILOT -> AiCopilotScreen(viewModel = viewModel)
                NavigationScreen.KNOWLEDGE_BASE -> KnowledgeScreen(viewModel = viewModel)
                NavigationScreen.EXCHANGE_SETTINGS -> ExchangeSettingsScreen(viewModel = viewModel)
            }
        }
    }
}
