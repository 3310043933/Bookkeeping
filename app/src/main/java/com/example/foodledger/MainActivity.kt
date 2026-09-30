package com.example.foodledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.foodledger.ui.FoodLedgerApp
import com.example.foodledger.ui.theme.FoodLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FoodLedgerTheme { FoodLedgerApp() } }
    }
}
