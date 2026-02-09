package us.bergnet.oversight.ui.setup

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFF1A1A2E)
private val CardColor = Color(0xFF2A2A4E)
private val AccentColor = Color(0xFF6C63FF)

@Composable
fun OnboardingScreen(
    hasOverlayPermission: Boolean,
    hasBatteryExemption: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    // Auto-advance logic
    if (currentStep == 1 && hasOverlayPermission) {
        currentStep = 2
    }
    if (currentStep == 2 && hasBatteryExemption) {
        onComplete()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(CardColor, RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> WelcomeStep(onNext = { currentStep = 1 })
                1 -> OverlayPermissionStep(
                    onGrant = onRequestOverlayPermission,
                    onSkip = { currentStep = 2 }
                )
                2 -> BatteryStep(
                    onRequest = onRequestBatteryExemption,
                    onSkip = { onComplete() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step indicator
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { idx ->
                    Box(
                        modifier = Modifier
                            .width(if (idx == currentStep) 24.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                if (idx == currentStep) AccentColor else Color(0xFF555577),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    Text(
        text = "OverSight",
        color = AccentColor,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "OverSight displays overlay notifications on your TV, controlled via a REST API on your local network.",
        color = Color.White,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onNext,
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.focusRequester(focusRequester)
    ) {
        Text("Get Started", fontSize = 16.sp)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun OverlayPermissionStep(
    onGrant: () -> Unit,
    onSkip: () -> Unit
) {
    val grantFocus = remember { FocusRequester() }

    Text(
        text = "Overlay Permission",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "OverSight needs permission to draw over other apps to display the overlay clock and notifications.\n\nAfter pressing Grant, scroll through the app list to find OverSight and enable the permission.",
        color = Color(0xFFB0B0B0),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onGrant,
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.focusRequester(grantFocus)
    ) {
        Text("Grant Permission", fontSize = 15.sp)
    }
    Spacer(modifier = Modifier.height(8.dp))
    SkipButton(onClick = onSkip)

    LaunchedEffect(Unit) {
        grantFocus.requestFocus()
    }
}

@Composable
private fun BatteryStep(
    onRequest: () -> Unit,
    onSkip: () -> Unit
) {
    val requestFocus = remember { FocusRequester() }

    Text(
        text = "Battery Optimization",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "To keep the overlay running reliably, exempt OverSight from battery optimization. This prevents Android from killing the service.",
        color = Color(0xFFB0B0B0),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onRequest,
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.focusRequester(requestFocus)
    ) {
        Text("Request Exemption", fontSize = 15.sp)
    }
    Spacer(modifier = Modifier.height(8.dp))
    SkipButton(onClick = onSkip)

    LaunchedEffect(Unit) {
        requestFocus.requestFocus()
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(
                    2.dp,
                    AccentColor,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            ),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (focused) Color.White else Color(0xFF888888)
        )
    ) {
        Text("Skip", fontSize = 14.sp)
    }
}
