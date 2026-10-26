package com.cableops.stbexpiry.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cableops.stbexpiry.data.model.ExpiryRecord
import com.cableops.stbexpiry.ui.theme.BadgeGreen
import com.cableops.stbexpiry.ui.theme.BadgeRed
import com.cableops.stbexpiry.ui.theme.BadgeYellow

/**
 * Displays a single [ExpiryRecord] as a Material 3 ElevatedCard.
 *
 * The expiry date badge is colour-coded:
 * - **Red**    → expired or expiring today
 * - **Yellow** → expiring within the next 3 days
 * - **Green**  → more than 3 days away
 *
 * The STB number row contains a one-tap copy button that writes the number
 * to the system clipboard.
 */
@Composable
fun RecordCard(
    record: ExpiryRecord,
    isExpiredOrToday: Boolean,
    isUpcoming: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeColor by animateColorAsState(
        targetValue = when {
            isExpiredOrToday -> BadgeRed
            isUpcoming       -> BadgeYellow
            else             -> BadgeGreen
        },
        animationSpec = tween(durationMillis = 400),
        label = "BadgeColorAnim"
    )

    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Customer Name ─────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.Person,
                    contentDescription = "Customer",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text       = record.name,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── STB Number row with copy button ───────────────────────────────────
            Row(
                modifier       = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.Router,
                        contentDescription = "STB Number",
                        tint               = MaterialTheme.colorScheme.secondary,
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text  = record.stbNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick  = { clipboardManager.setText(AnnotatedString(record.stbNumber)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.ContentCopy,
                        contentDescription = "Copy STB Number",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Expiry date badge ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text      = record.endDate,
                        color     = Color.White,
                        fontSize  = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
