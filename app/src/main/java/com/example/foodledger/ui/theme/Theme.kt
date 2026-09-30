package com.example.foodledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFFB8502C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFF6F5B52),
    background = Color(0xFFFFF9F5),
    surface = Color(0xFFFFF9F5),
    surfaceVariant = Color(0xFFF5DED5)
)

@Composable
fun FoodLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
