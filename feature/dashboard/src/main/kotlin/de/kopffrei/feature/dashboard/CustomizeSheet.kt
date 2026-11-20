package de.kopffrei.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kopffrei.core.common.settings.DashboardLayout
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfSwitchRow
import de.kopffrei.core.designsystem.components.minTouchSize

/** Blendet Startseiten-Karten ein/aus und ändert ihre Reihenfolge. */
@Composable
internal fun CustomizeSheet(
    settings: UserSettings,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Startseite anpassen", style = MaterialTheme.typography.titleLarge)
            Text(
                "Blende Karten aus, die du nicht brauchst, und schiebe die wichtigen nach oben.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val order = DashboardLayout.fullOrder(settings)
            order.forEachIndexed { index, card ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchSize()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = card !in settings.dashboardHidden,
                        onCheckedChange = { visible ->
                            onUpdate { s ->
                                s.copy(
                                    dashboardHidden = if (visible) s.dashboardHidden - card else s.dashboardHidden + card,
                                )
                            }
                        },
                    )
                    Text(card.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        onClick = {
                            onUpdate { s ->
                                s.copy(dashboardOrder = DashboardLayout.move(DashboardLayout.fullOrder(s), card, -1))
                            }
                        },
                        enabled = index > 0,
                    ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "${card.label} nach oben") }
                    IconButton(
                        onClick = {
                            onUpdate { s ->
                                s.copy(dashboardOrder = DashboardLayout.move(DashboardLayout.fullOrder(s), card, +1))
                            }
                        },
                        enabled = index < order.lastIndex,
                    ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "${card.label} nach unten") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            KfSwitchRow(
                label = "Kompakte Ansicht",
                checked = settings.dashboardCompact,
                onCheckedChange = { value -> onUpdate { it.copy(dashboardCompact = value) } },
                description = "Weniger Text und engere Abstände.",
            )
            KfSwitchRow(
                label = "Aufmunternde Sätze anzeigen",
                checked = settings.showMotivation,
                onCheckedChange = { value -> onUpdate { it.copy(showMotivation = value) } },
            )
            KfButton("Fertig", onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
