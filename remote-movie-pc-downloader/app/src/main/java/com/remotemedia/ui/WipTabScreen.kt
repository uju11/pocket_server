package com.remotemedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.ui.theme.TacticalBorder
import com.remotemedia.ui.theme.TacticalCanvas
import com.remotemedia.ui.theme.TacticalMuted
import com.remotemedia.ui.theme.TacticalOrange
import com.remotemedia.ui.theme.TacticalPanel
import com.remotemedia.ui.theme.TacticalSubtle
import com.remotemedia.ui.theme.TacticalText

@Composable
fun WipTabScreen(tabName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .background(TacticalPanel, RoundedCornerShape(8.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(TacticalOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalOrange, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "[ WIP ]",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalOrange
                )
            }

            Text(
                text = tabName.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = TacticalText
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TacticalBorder)
            )

            Text(
                text = "// UNDER CONSTRUCTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted
            )
            Text(
                text = "This module is yet to be defined.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalSubtle
            )
            Text(
                text = "Stay tuned for next deployment.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalSubtle
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(5.dp).background(TacticalOrange, RoundedCornerShape(50)))
                Box(modifier = Modifier.size(5.dp).background(TacticalBorder, RoundedCornerShape(50)))
                Box(modifier = Modifier.size(5.dp).background(TacticalBorder, RoundedCornerShape(50)))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "// REV 2026.09 -- NEXT SPRINT",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = TacticalSubtle
        )
    }
}
