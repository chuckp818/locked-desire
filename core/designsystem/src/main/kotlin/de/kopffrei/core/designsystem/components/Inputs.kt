package de.kopffrei.core.designsystem.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kopffrei.core.common.time.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ------------------------------------------------------------------ Texteingabe

@Composable
fun KfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ------------------------------------------------------------------ Datum und Uhrzeit

/** Datumsauswahl. Wert = Beginn des Tages in Millisekunden (lokale Zeitzone) oder null. */
@Composable
fun DatePickerField(
    label: String,
    dateMillis: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
) {
    val zone = remember { ZoneId.systemDefault() }
    var pickerOpen by remember { mutableStateOf(false) }
    val text = dateMillis
        ?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(TimeUtils.toLocalDate(it, zone)) }
        ?: "kein Datum"

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { pickerOpen = true },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minTouchSize()),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("$label: $text", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (clearable && dateMillis != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Rounded.Close, contentDescription = "$label entfernen")
            }
        }
    }

    if (pickerOpen) {
        val initial = dateMillis?.let {
            TimeUtils.toLocalDate(it, zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = state.selectedDateMillis
                        if (picked != null) {
                            val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(TimeUtils.startOfDay(date, zone))
                        }
                        pickerOpen = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Uhrzeit (24 Stunden). Wert = Minuten seit Mitternacht oder null. */
@Composable
fun TimePickerField(
    label: String,
    minutesOfDay: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val text = minutesOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "keine Uhrzeit"

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { pickerOpen = true },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minTouchSize()),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("$label: $text", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (clearable && minutesOfDay != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Rounded.Close, contentDescription = "$label entfernen")
            }
        }
    }

    if (pickerOpen) {
        val initial = minutesOfDay ?: (9 * 60)
        val state = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(state.hour * 60 + state.minute)
                        pickerOpen = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = state) },
        )
    }
}

// ------------------------------------------------------------------ Spracheingabe

/**
 * Startet die Android-Spracherkennung (Systemdialog, keine eigene Mikrofon-Berechtigung nötig) und
 * liefert den erkannten Text. Gibt die Funktion zum Starten zurück.
 */
@Composable
fun rememberVoiceInput(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) currentOnResult(text)
        }
    }
    return remember(launcher, context) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich einfach los …")
            }
            try {
                launcher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Auf diesem Gerät ist keine Spracherkennung verfügbar.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

@Composable
fun VoiceInputButton(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val start = rememberVoiceInput(onResult)
    IconButton(onClick = start, modifier = modifier) {
        Icon(Icons.Rounded.Mic, contentDescription = "Einsprechen")
    }
}

// ------------------------------------------------------------------ Anhänge

class AttachmentPickers(val pickImage: () -> Unit, val pickFile: () -> Unit)

@Composable
fun rememberAttachmentPickers(onPicked: (Uri) -> Unit): AttachmentPickers {
    val current by rememberUpdatedState(onPicked)
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) current(uri)
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) current(uri)
    }
    return remember(imageLauncher, fileLauncher) {
        AttachmentPickers(
            pickImage = { imageLauncher.launch("image/*") },
            pickFile = { fileLauncher.launch("*/*") },
        )
    }
}

data class AttachmentUi(
    val id: String,
    val name: String,
    val isImage: Boolean,
    val file: File,
)

fun openAttachmentExternally(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Keine App zum Öffnen dieser Datei gefunden.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun AttachmentsSection(
    items: List<AttachmentUi>,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Anhänge")
        if (items.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item ->
                    Column(Modifier.width(140.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                .clickable { onOpen(item.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.isImage) {
                                AsyncFileImage(
                                    file = item.file,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(88.dp),
                                    contentDescription = item.name,
                                )
                            } else {
                                Icon(Icons.Rounded.AttachFile, contentDescription = item.name)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemove(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Anhang ${item.name} entfernen")
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KfButton("Bild", onAddImage, icon = Icons.Rounded.Image, style = KfButtonStyle.Tonal)
            KfButton("Datei", onAddFile, icon = Icons.Rounded.AttachFile, style = KfButtonStyle.Tonal)
        }
    }
}

/** Lädt ein Bild verkleinert im Hintergrund (ohne zusätzliche Bibliothek). */
@Composable
fun AsyncFileImage(
    file: File,
    modifier: Modifier = Modifier,
    maxPixels: Int = 400,
    contentDescription: String? = null,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxPixels)?.asImageBitmap() }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private fun decodeSampled(file: File, maxPixels: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPixels && bounds.outHeight / (sample * 2) >= maxPixels) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

/** Kleine Hilfe für Abstände in Spalten. */
@Composable
fun VerticalGap(height: Dp = 8.dp) {
    Spacer(Modifier.height(height))
}
