package com.example.soillab.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soillab.R
import kotlinx.coroutines.delay

@Composable
fun BootScreen() {
    var currentStep by remember { mutableStateOf(0) }
    val bootSteps = listOf(
        R.string.boot_os,
        R.string.boot_neural,
        R.string.boot_sensors,
        R.string.boot_models,
        R.string.boot_ready
    )

    val progress by animateFloatAsState(
        targetValue = (currentStep + 1) / bootSteps.size.toFloat(),
        animationSpec = tween(400), label = "BootProgress"
    )

    LaunchedEffect(Unit) {
        for (i in bootSteps.indices) {
            delay(500) // Total time 5 * 500 = 2500ms
            currentStep = i
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Grain,
            contentDescription = "Soil Master Logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Soil Master",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 6.dp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(400)) { height -> height } + fadeIn(animationSpec = tween(400)))
                    .togetherWith(slideOutVertically(animationSpec = tween(400)) { height -> -height } + fadeOut(animationSpec = tween(400)))
            }, label = "BootTextAnimation"
        ) { stepIndex ->
            Text(
                text = stringResource(bootSteps[stepIndex]),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

