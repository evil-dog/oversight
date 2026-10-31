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
import us.bergnet.oversight.ui.theme.Accent
import us.bergnet.oversight.ui.theme.Background
import us.bergnet.oversight.ui.theme.Surface
import us.bergnet.oversight.ui.theme.TextMuted
import us.bergnet.oversight.ui.theme.TextSecondary


enum class OnboardingStep { WELCOME, OVERLAY, BATTERY, BUTTON_REMAP }

/**
 * Runs [steps] in order, then calls [onComplete]. First launch gets every applicable step; an
 * existing install gets only the intro steps for features added since ([isFeatureIntro], see SetupActivity).
 */
@Composable
fun OnboardingScreen(
    steps: List<OnboardingStep>,
    isFeatureIntro: Boolean,
    hasOverlayPermission: Boolean,
    hasBatteryExemption: Boolean,
    hasRemapService: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestRemapService: () -> Unit,
    onComplete: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrNull(currentIndex)
    val next: () -> Unit = { if (currentIndex < steps.lastIndex) currentIndex++ else onComplete() }

    // Auto-advance when a permission is granted (e.g. on returning from Settings). Runs as effects:
    // state writes and onComplete() during composition can repeat on every recomposition.
    LaunchedEffect(currentStep, hasOverlayPermission, hasBatteryExemption, hasRemapService) {
        val granted = when (currentStep) {
            OnboardingStep.OVERLAY -> hasOverlayPermission
            OnboardingStep.BATTERY -> hasBatteryExemption
            OnboardingStep.BUTTON_REMAP -> hasRemapService
            else -> false
        }
        if (granted) next()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(Surface, RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep(onNext = next)
                OnboardingStep.OVERLAY -> OverlayPermissionStep(
                    onGrant = onRequestOverlayPermission,
                    onSkip = next
                )
                OnboardingStep.BATTERY -> BatteryStep(
                    onRequest = onRequestBatteryExemption,
                    onSkip = next
                )
                OnboardingStep.BUTTON_REMAP -> ButtonRemapStep(
                    isFeatureIntro = isFeatureIntro,
                    onEnable = onRequestRemapService,
                    onSkip = next
                )
                null -> {}
            }

            if (steps.size > 1) {
                Spacer(modifier = Modifier.height(24.dp))

                // Step indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(steps.size) { idx ->
                        Box(
                            modifier = Modifier
                                .width(if (idx == currentIndex) 24.dp else 8.dp)
                                .height(8.dp)
                                .background(
                                    if (idx == currentIndex) Accent else Color(0xFF555577),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
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
        color = Accent,
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
        colors = ButtonDefaults.buttonColors(containerColor = Accent),
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
        color = TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onGrant,
        colors = ButtonDefaults.buttonColors(containerColor = Accent),
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
        color = TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onRequest,
        colors = ButtonDefaults.buttonColors(containerColor = Accent),
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
private fun ButtonRemapStep(
    isFeatureIntro: Boolean,
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    val enableFocus = remember { FocusRequester() }

    if (isFeatureIntro) {
        Text(
            text = "NEW",
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text(
        text = "Remote Button Remapping",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "OverSight can reassign your remote's buttons, like opening your favorite app from the Netflix " +
            "button or making a long press of Back go Home. This needs OverSight's accessibility service.\n\n" +
            "After pressing Enable, select \"OverSight Button Remapping\" and turn it on. If it's greyed out, " +
            "first choose \"Allow restricted settings\" from OverSight's App info menu.",
        color = TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Optional. You can turn it on later under Remote Buttons in settings.",
        color = TextMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onEnable,
        colors = ButtonDefaults.buttonColors(containerColor = Accent),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.focusRequester(enableFocus)
    ) {
        Text("Enable", fontSize = 15.sp)
    }
    Spacer(modifier = Modifier.height(8.dp))
    SkipButton(onClick = onSkip, label = if (isFeatureIntro) "Not Now" else "Skip")

    LaunchedEffect(Unit) {
        enableFocus.requestFocus()
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit, label: String = "Skip") {
    var focused by remember { mutableStateOf(false) }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(
                    2.dp,
                    Accent,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            ),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (focused) Color.White else Color(0xFF888888)
        )
    ) {
        Text(label, fontSize = 14.sp)
    }
}
