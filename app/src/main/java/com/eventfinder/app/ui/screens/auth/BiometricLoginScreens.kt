package com.eventfinder.app.ui.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventfinder.app.R
import com.eventfinder.app.security.BiometricMethods

/** Which panel of the biometric flow is on screen. */
enum class BiometricStage { METHODS, SUCCESS, FAILURE }

/**
 * Full-screen biometric flow that matches the 2026 design sheet.
 *
 * The fingerprint/face check itself is the system prompt (an app cannot draw
 * that securely); these panels surround it — offering the methods the device
 * actually has, and reporting the real outcome afterwards.
 */
@Composable
fun BiometricLoginScreen(
    methods: BiometricMethods,
    stage: BiometricStage,
    failureMessage: String?,
    onAuthenticate: (() -> Unit)?,
    onUsePassword: () -> Unit,
    onContinue: () -> Unit,
    onNeedHelp: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Ambient green glow behind the emblem, as in the reference.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.primary.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.26f),
                    radius = size.minDimension * 0.6f
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(size.width / 2f, size.height * 0.26f)
            )
        }
        SkylineScrim(Modifier.align(Alignment.BottomCenter))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onUsePassword,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.onBackground
                )
            }

            Spacer(Modifier.height(24.dp))

            when (stage) {
                BiometricStage.METHODS -> MethodsPanel(methods, onAuthenticate, onUsePassword)
                BiometricStage.SUCCESS -> SuccessPanel(onContinue)
                BiometricStage.FAILURE -> FailurePanel(
                    message = failureMessage,
                    onTryAgain = onAuthenticate,
                    onUsePassword = onUsePassword,
                    onNeedHelp = onNeedHelp
                )
            }
        }
    }
}

@Composable
private fun MethodsPanel(
    methods: BiometricMethods,
    onAuthenticate: (() -> Unit)?,
    onUsePassword: () -> Unit
) {
    GlowRing(color = MaterialTheme.colorScheme.primary, size = 132.dp) {
        Icon(
            Icons.Outlined.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
    }
    Spacer(Modifier.height(28.dp))
    Text(
        stringResource(R.string.biometrics_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.biometrics_subtitle),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MethodTile(
            icon = Icons.Outlined.Fingerprint,
            label = stringResource(R.string.biometric_method_fingerprint),
            enabled = methods.hasFingerprint,
            onClick = onAuthenticate,
            modifier = Modifier.weight(1f)
        )
        MethodTile(
            icon = Icons.Outlined.Face,
            label = stringResource(R.string.biometric_method_face),
            enabled = methods.hasFace,
            onClick = onAuthenticate,
            modifier = Modifier.weight(1f)
        )
        MethodTile(
            icon = Icons.Outlined.Pin,
            label = stringResource(R.string.biometric_method_pin),
            enabled = methods.hasDeviceCredential,
            onClick = onAuthenticate,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(32.dp))
    OrDivider()
    Spacer(Modifier.height(20.dp))

    OutlinedButton(
        onClick = onUsePassword,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape
    ) {
        Text(stringResource(R.string.use_password_instead))
    }
}

@Composable
private fun SuccessPanel(onContinue: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    GlowRing(color = primary, size = 180.dp) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(86.dp)
        )
    }
    Spacer(Modifier.height(32.dp))
    Text(
        stringResource(R.string.biometric_success_title),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.biometric_success_hint),
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(36.dp))
    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = CircleShape
    ) {
        Text(
            stringResource(R.string.continue_to_home),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FailurePanel(
    message: String?,
    onTryAgain: (() -> Unit)?,
    onUsePassword: () -> Unit,
    onNeedHelp: () -> Unit
) {
    val error = MaterialTheme.colorScheme.error
    GlowRing(color = error, size = 180.dp) {
        Icon(
            Icons.Outlined.Fingerprint,
            contentDescription = null,
            tint = error,
            modifier = Modifier.size(86.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(error),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "!",
                color = MaterialTheme.colorScheme.onError,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
    Spacer(Modifier.height(32.dp))
    Text(
        stringResource(R.string.biometric_failed_title),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(10.dp))
    Text(
        message ?: stringResource(R.string.biometric_failed_hint),
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = { onTryAgain?.invoke() },
        enabled = onTryAgain != null,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(
            stringResource(R.string.try_again),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onUsePassword,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape
    ) {
        Text(stringResource(R.string.use_password))
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onNeedHelp) {
        Text(
            stringResource(R.string.need_help),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MethodTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = { if (enabled) onClick?.invoke() },
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(18.dp)
                )
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GlowRing(
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.22f), Color.Transparent)
                ),
                CircleShape
            )
            .border(2.dp, color.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
        Text(
            stringResource(R.string.auth_or),
            modifier = Modifier.padding(horizontal = 12.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
    }
}

/** Decorative skyline band shared by every biometric panel. */
@Composable
private fun SkylineScrim(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outline
    val tint = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val heights = listOf(0.30f, 0.55f, 0.38f, 0.72f, 0.46f, 0.62f, 0.34f, 0.80f, 0.42f, 0.58f, 0.30f, 0.66f)
        val step = size.width / heights.size
        heights.forEachIndexed { index, ratio ->
            val buildingHeight = size.height * ratio
            drawRect(
                color = line.copy(alpha = 0.22f),
                topLeft = Offset(index * step + step * 0.12f, size.height - buildingHeight),
                size = Size(step * 0.76f, buildingHeight)
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, tint.copy(alpha = 0.05f))
            ),
            size = size
        )
    }
}
