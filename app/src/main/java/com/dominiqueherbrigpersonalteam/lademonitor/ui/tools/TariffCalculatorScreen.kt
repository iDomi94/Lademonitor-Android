package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.TariffBasis
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.TariffCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.TariffCalculatorSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import kotlin.math.abs
import kotlin.math.roundToInt

private val Red = Color(0xFFFF3B30)
private const val FALLBACK_CONSUMPTION = 18.0
private val PRICE_RANGE = 0.20..1.00
private val FEE_RANGE = 0.0..30.0
private val SHARE_RANGE = 0.0..100.0
private val KM_RANGE = 0.0..5000.0

/**
 * Vorschlaege, einmal je Anbieter-/Lade-Art-Wechsel berechnet, nicht bei jeder Reglerbewegung -
 * die laufen ueber alle Ladevorgaenge.
 */
private data class Suggestions(
    val km: Double? = null,
    val consumption: Double? = null,
    val sharePct: Double? = null,
    /** Der Anteil stammt vom gewaehlten Anbieter selbst (sonst: alle oeffentlichen Anbieter). */
    val shareFromProvider: Boolean = false,
    val price: Double? = null,
    val fee: Double? = null,
    val comparison: TariffCalculator.PaidPrice? = null
)

private fun makeSuggestions(basis: TariffBasis?, providerId: String?, chargingType: ChargingType): Suggestions {
    if (basis == null) return Suggestions()
    val sessions = basis.sessions
    val publicShare = TariffCalculator.suggestedSharePct(sessions) { session ->
        val id = session.providerId ?: return@suggestedSharePct false
        id !in basis.excludedProviderIds
    }
    var share = publicShare
    var shareFromProvider = false
    var price: Double? = null
    var fee: Double? = null
    if (providerId != null) {
        // Ein Anbieter ohne eigene Ladungen (z. B. gerade erst angelegt, um ein Abo
        // durchzurechnen) bekaeme sonst 0 % - dann ist der Anteil des oeffentlichen Ladens die
        // bessere Schaetzung.
        val own = TariffCalculator.suggestedSharePct(sessions) { it.providerId == providerId }
        if (own != null && own > 0) {
            share = own
            shareFromProvider = true
        }
        val provider = basis.providers.firstOrNull { it.id == providerId }
        price = if (chargingType == ChargingType.DC) provider?.lastPriceDcPerKwh else provider?.lastPriceAcPerKwh
        fee = TariffCalculator.suggestedMonthlyFee(providerId, basis.fees)
    }
    return Suggestions(
        km = TariffCalculator.suggestedKmPerMonth(sessions),
        consumption = TariffCalculator.suggestedConsumption(sessions),
        sharePct = share?.let { it.roundToInt().toDouble() },
        shareFromProvider = shareFromProvider,
        price = price,
        fee = fee,
        comparison = TariffCalculator.automaticComparisonPrice(
            sessions, basis.excludedProviderIds, providerId, chargingType
        )
    )
}

private enum class EditField { PRICE, FEE, SHARE, KM }

private fun euro(v: Double) = Fmt.n("%.2f", v) + " €"
private fun perKwh(v: Double) = euro(v) + "/kWh"
private fun kmText(v: Double) = Fmt.n("%,.0f", v) + " km"
private fun percent(v: Double) = Fmt.n("%.0f", v) + " %"
private fun parseNumber(text: String): Double? =
    text.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it >= 0 }

/**
 * "Lohnt sich der Tarif?" - Gegenstueck zu `TariffCalculatorView.swift`: rechnet voraus, was eine
 * kWh mit Grundgebuehr effektiv kostet und ob das guenstiger ist als ohne Tarif.
 *
 * In erster Linie eine Spielwiese: der Rechner braucht keinen bekannten Anbieter. "Werte
 * uebernehmen von" fuellt Preis, Grundgebuehr und Anteil nur vor. Gerechnet wird bei jeder
 * Reglerbewegung neu, das Ergebnis steht oben und bleibt beim Scrollen sichtbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TariffCalculatorScreen(navController: NavController, initialProviderId: String? = null) {
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    var basis by remember { mutableStateOf<TariffBasis?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // rememberSaveable: die Eingaben muessen den Ausflug zu "Oeffentliche Anbieter" ueberleben.
    var didLoad by rememberSaveable { mutableStateOf(false) }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    var chargingType by rememberSaveable { mutableStateOf(ChargingType.DC) }
    // Beispielwerte fuer einen neuen Tarif, bis Vorschlaege oder ein Anbieter sie ersetzen.
    var price by rememberSaveable { mutableStateOf(0.49) }
    var fee by rememberSaveable { mutableStateOf(10.0) }
    var sharePct by rememberSaveable { mutableStateOf(30.0) }
    var km by rememberSaveable { mutableStateOf(1000.0) }
    var consumptionText by rememberSaveable { mutableStateOf("") }
    var automatic by remember { mutableStateOf(TariffCalculatorSettings.automaticComparison) }
    var manualText by remember { mutableStateOf(TariffCalculatorSettings.manualComparisonText) }
    var editing by remember { mutableStateOf<EditField?>(null) }
    var editText by remember { mutableStateOf("") }
    var providerMenu by remember { mutableStateOf(false) }

    val suggestions = remember(basis, providerId, chargingType) { makeSuggestions(basis, providerId, chargingType) }

    /** Anbieterwechsel: beim Wechsel auf "Neuer Tarif" bleiben Preis und Grundgebuehr stehen. */
    fun applyProviderValues(s: Suggestions, pid: String?) {
        s.sharePct?.let { sharePct = it }
        if (pid == null) return
        s.price?.let { price = it }
        fee = s.fee ?: 0.0
    }

    LaunchedEffect(Unit) {
        // Beim ersten Mal alle Regler aus den Vorschlaegen setzen, danach (Rueckkehr aus
        // "Oeffentliche Anbieter") nur neu laden.
        if (!AppSettings.isReadyForDataAccess) { loadError = serverAddressRequiredMessage; return@LaunchedEffect }
        try {
            val loaded = AppRepository.tariffBasis()
            basis = loaded
            loadError = null
            if (!didLoad) {
                didLoad = true
                if (initialProviderId != null && loaded.providers.any { it.id == initialProviderId }) {
                    providerId = initialProviderId
                }
                val s = makeSuggestions(loaded, providerId, chargingType)
                s.km?.let { km = it }
                applyProviderValues(s, providerId)
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage
        }
    }

    val consumption = parseNumber(consumptionText) ?: suggestions.consumption ?: FALLBACK_CONSUMPTION
    val comparisonPrice = if (automatic) suggestions.comparison?.pricePerKwh else parseNumber(manualText)
    val result = TariffCalculator.compute(
        TariffCalculator.Input(price, fee, sharePct, km, consumption, comparisonPrice)
    )
    val selectedProvider = basis?.providers?.firstOrNull { it.id == providerId }

    // km-Regler: links vom Break-even rot, rechts gruen. Lohnt sich der Tarif bei keiner
    // Fahrleistung, ist die ganze Spur rot.
    val breakEvenKm = result.breakEvenKm
    val kmSplit = when {
        result.neverPaysOff -> SliderSplit(KM_RANGE.endInclusive, Red.copy(alpha = 0.75f), Green.copy(alpha = 0.75f))
        breakEvenKm != null -> SliderSplit(breakEvenKm, Red.copy(alpha = 0.75f), Green.copy(alpha = 0.75f))
        else -> null
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_tariff_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ResultCard(result, fee, price)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                loadError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                // Tarif
                SectionCard {
                    SectionHeader(stringResource(R.string.tariff_section_tariff))
                    Text(stringResource(R.string.tariff_take_values_from), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedProvider?.name ?: stringResource(R.string.tariff_new))
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tariff_new)) },
                                onClick = {
                                    providerMenu = false
                                    providerId = null
                                    applyProviderValues(makeSuggestions(basis, null, chargingType), null)
                                }
                            )
                            basis?.providers?.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        providerMenu = false
                                        providerId = provider.id
                                        applyProviderValues(makeSuggestions(basis, provider.id, chargingType), provider.id)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ChargingType.entries.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = chargingType == type,
                                onClick = {
                                    chargingType = type
                                    if (providerId != null) {
                                        makeSuggestions(basis, providerId, type).price?.let { price = it }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, ChargingType.entries.size)
                            ) { Text(type.raw) }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    SliderRow(
                        title = stringResource(R.string.tariff_label_price),
                        valueText = perKwh(price),
                        value = price,
                        onValueChange = { price = it },
                        range = PRICE_RANGE,
                        step = 0.01,
                        marker = suggestions.price,
                        caption = selectedProvider?.let { p ->
                            suggestions.price?.let { stringResource(R.string.tariff_caption_price, perKwh(it), chargingType.raw, p.name) }
                                ?: stringResource(R.string.tariff_caption_price_none, p.name, chargingType.raw)
                        },
                        onReset = resetAction(price, suggestions.price) { price = it },
                        onEdit = { editText = Fmt.n("%.2f", price); editing = EditField.PRICE }
                    )
                    SliderRow(
                        title = stringResource(R.string.tariff_label_fee),
                        valueText = euro(fee),
                        value = fee,
                        onValueChange = { fee = it },
                        range = FEE_RANGE,
                        step = 0.5,
                        marker = suggestions.fee,
                        caption = selectedProvider?.let { p ->
                            suggestions.fee?.let { stringResource(R.string.tariff_caption_fee, euro(it), p.name) }
                                ?: stringResource(R.string.tariff_caption_fee_none, p.name)
                        },
                        onReset = resetAction(fee, suggestions.fee) { fee = it },
                        onEdit = { editText = Fmt.n("%.2f", fee); editing = EditField.FEE }
                    )
                    Footnote(stringResource(R.string.tariff_tariff_footer))
                }

                // Fahrprofil
                SectionCard {
                    SectionHeader(stringResource(R.string.tariff_section_profile))
                    SliderRow(
                        title = stringResource(R.string.tariff_label_km),
                        valueText = kmText(km),
                        value = km,
                        onValueChange = { km = it },
                        range = KM_RANGE,
                        step = 50.0,
                        marker = suggestions.km,
                        split = kmSplit,
                        caption = suggestions.km?.let { stringResource(R.string.tariff_caption_km, kmText(it)) }
                            ?: stringResource(R.string.tariff_caption_km_none),
                        onReset = resetAction(km, suggestions.km) { km = it },
                        onEdit = { editText = km.roundToInt().toString(); editing = EditField.KM }
                    )
                    SliderRow(
                        title = stringResource(R.string.tariff_label_share),
                        valueText = percent(sharePct),
                        value = sharePct,
                        onValueChange = { sharePct = it },
                        range = SHARE_RANGE,
                        step = 5.0,
                        marker = suggestions.sharePct,
                        caption = suggestions.sharePct.let { suggested ->
                            when {
                                suggested == null -> stringResource(R.string.tariff_caption_share_none)
                                suggestions.shareFromProvider && selectedProvider != null ->
                                    stringResource(R.string.tariff_caption_share_provider, percent(suggested), selectedProvider.name)
                                else -> stringResource(R.string.tariff_caption_share_public, percent(suggested))
                            }
                        },
                        onReset = resetAction(sharePct, suggestions.sharePct) { sharePct = it },
                        onEdit = { editText = Fmt.n("%.0f", sharePct); editing = EditField.SHARE }
                    )
                    OutlinedTextField(
                        value = consumptionText,
                        onValueChange = { consumptionText = it },
                        label = { Text(stringResource(R.string.tariff_label_consumption)) },
                        // Grauer Platzhalter = Vorschlag; solange das Feld leer ist, wird damit gerechnet.
                        placeholder = { Text(Fmt.n("%.1f", suggestions.consumption ?: FALLBACK_CONSUMPTION)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Footnote(
                        if (suggestions.consumption != null) stringResource(R.string.tariff_caption_consumption)
                        else stringResource(R.string.tariff_caption_consumption_none)
                    )
                    Footnote(stringResource(R.string.tariff_profile_footer))
                }

                // Preis ohne Tarif
                SectionCard {
                    SectionHeader(stringResource(R.string.tariff_section_comparison))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(true, false).forEachIndexed { index, auto ->
                            SegmentedButton(
                                selected = automatic == auto,
                                onClick = { automatic = auto; TariffCalculatorSettings.automaticComparison = auto },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) {
                                Text(stringResource(if (auto) R.string.tariff_mode_automatic else R.string.tariff_mode_manual))
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    if (automatic) {
                        val comparison = suggestions.comparison
                        if (comparison != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.tariff_auto_value, chargingType.raw))
                                Text(perKwh(comparison.pricePerKwh), fontWeight = FontWeight.SemiBold)
                            }
                            Footnote(stringResource(R.string.tariff_auto_basis, comparison.sessionCount))
                        } else {
                            Text(stringResource(R.string.tariff_auto_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { navController.navigate("tools/public-providers") }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.tariff_choose_public), color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Footnote(stringResource(R.string.tariff_auto_footer))
                    } else {
                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it; TariffCalculatorSettings.manualComparisonText = it },
                            label = { Text(stringResource(R.string.tariff_manual_label)) },
                            placeholder = { Text(stringResource(R.string.tariff_manual_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Footnote(stringResource(R.string.tariff_manual_footer))
                    }
                }
            }
        }
    }

    editing?.let { field ->
        val title = when (field) {
            EditField.PRICE -> R.string.tariff_edit_price
            EditField.FEE -> R.string.tariff_edit_fee
            EditField.SHARE -> R.string.tariff_edit_share
            EditField.KM -> R.string.tariff_edit_km
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(title)) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text(stringResource(R.string.tariff_value_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field == EditField.KM || field == EditField.SHARE) KeyboardType.Number else KeyboardType.Decimal
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (field) {
                        EditField.KM -> editText.filter { it.isDigit() }.toDoubleOrNull()?.let { km = it }
                        EditField.SHARE -> parseNumber(editText)?.let { sharePct = it.coerceIn(0.0, 100.0) }
                        EditField.PRICE -> parseNumber(editText)?.let { price = it }
                        EditField.FEE -> parseNumber(editText)?.let { fee = it }
                    }
                    editing = null
                }) { Text(stringResource(R.string.tariff_apply)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

private fun resetAction(value: Double, suggestion: Double?, apply: (Double) -> Unit): (() -> Unit)? {
    if (suggestion == null || abs(value - suggestion) <= 0.0001) return null
    return { apply(suggestion) }
}

@Composable
private fun ResultCard(result: TariffCalculator.Result, fee: Double, price: Double) {
    val tint = when (result.verdict) {
        TariffCalculator.Verdict.CHEAPER -> Green
        TariffCalculator.Verdict.MORE_EXPENSIVE -> Red
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(tint.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, tint.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tariff_effective_price), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.effectivePricePerKwh?.let { perKwh(it) } ?: "–", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                // Symbol UND Wort, damit das Ergebnis auch ohne Farbsehen eindeutig ist.
                val verdict = when (result.verdict) {
                    TariffCalculator.Verdict.CHEAPER -> Icons.Filled.CheckCircle to R.string.tariff_verdict_cheaper
                    TariffCalculator.Verdict.MORE_EXPENSIVE -> Icons.Filled.Cancel to R.string.tariff_verdict_more_expensive
                    TariffCalculator.Verdict.EQUAL -> Icons.Filled.DragHandle to R.string.tariff_verdict_equal
                    TariffCalculator.Verdict.UNKNOWN -> null
                }
                verdict?.let { (icon, label) ->
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(label), color = tint, fontWeight = FontWeight.SemiBold)
                }
            }
            val feePerKwh = result.feePerKwh
            val detail = if (result.kwhPerMonth > 0 && feePerKwh != null) {
                stringResource(R.string.tariff_detail, Fmt.n("%.0f", result.kwhPerMonth), perKwh(price), perKwh(feePerKwh))
            } else {
                stringResource(R.string.tariff_detail_no_kwh, euro(fee))
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val savings = result.savingsPerMonth
            val without = result.costWithoutTariff
            val savingsLine = if (savings == null || without == null) {
                stringResource(R.string.tariff_no_comparison)
            } else when (result.verdict) {
                TariffCalculator.Verdict.CHEAPER ->
                    stringResource(R.string.tariff_savings, euro(savings), euro(result.costWithTariff), euro(without))
                TariffCalculator.Verdict.MORE_EXPENSIVE ->
                    stringResource(R.string.tariff_extra_cost, euro(-savings), euro(result.costWithTariff), euro(without))
                else -> stringResource(R.string.tariff_equal_cost, euro(result.costWithTariff))
            }
            Text(savingsLine, style = MaterialTheme.typography.bodySmall)

            val beKwh = result.breakEvenKwh
            val beKm = result.breakEvenKm
            val breakEven = when {
                result.neverPaysOff -> stringResource(R.string.tariff_never)
                beKwh == null -> null
                beKwh <= 0 -> stringResource(R.string.tariff_no_fee_pays_off)
                beKm != null -> stringResource(
                    R.string.tariff_break_even_km,
                    kmText((beKm / 10).roundToInt() * 10.0),
                    Fmt.n("%.0f", beKwh)
                )
                else -> stringResource(R.string.tariff_break_even_kwh, Fmt.n("%.0f", beKwh))
            }
            breakEven?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Eine Zeile mit Titel, antippbarem Wert, Regler und grauer Herkunftszeile. */
@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    marker: Double?,
    caption: String?,
    onReset: (() -> Unit)?,
    onEdit: () -> Unit,
    split: SliderSplit? = null
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            if (onReset != null) {
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.tariff_reset), modifier = Modifier.size(18.dp))
                }
            }
            TextButton(onClick = onEdit) { Text(valueText, fontWeight = FontWeight.SemiBold) }
        }
        TariffSlider(
            value = value,
            onValueChange = onValueChange,
            range = range,
            step = step,
            label = title,
            stateText = valueText,
            marker = marker,
            split = split
        )
        caption?.let { Footnote(it) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
}
