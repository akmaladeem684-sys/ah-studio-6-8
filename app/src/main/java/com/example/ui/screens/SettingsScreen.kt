package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.StudioPreferencesManager
import com.example.domain.plugin.InstalledPlugin
import com.example.domain.plugin.PluginValidationResult
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsState()
  val installedPlugins by viewModel.installedPlugins.collectAsState()
  var cacheSizeText by remember { mutableStateOf("48.5 MB") }
  var selectedCategoryFilter by remember { mutableStateOf("All") }

  // ZIP File Picker Launcher
  val zipPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let {
      when (val result = viewModel.installPluginFromUri(it)) {
        is PluginValidationResult.Success -> {
          Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
        is PluginValidationResult.Error -> {
          Toast.makeText(context, "Plugin Install Failed: ${result.reason}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg),
    containerColor = StudioDarkBg,
    topBar = {
      TopAppBar(
        title = { Text("Studio Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioDarkBg)
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Pro Subscription Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = StudioSurface),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyanAccent, PurpleAccent)))
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("AH Studio Pro", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                  Text(if (settings.isProSubscriber) "Active Unlimited License" else "Free Tier", color = CyanAccent, fontSize = 12.sp)
                }
              }

              Button(
                onClick = {
                  StudioPreferencesManager.toggleProSubscription()
                  Toast.makeText(context, if (!settings.isProSubscriber) "Pro Subscription Activated!" else "Switched to Free Tier", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp)
              ) {
                Text(if (settings.isProSubscriber) "Pro Active" else "Upgrade", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }

            Text(
              text = "• 4K HDR 60fps export rendering\n• Unlimited AI auto-captions & translation\n• Complete access to all visual filters and sound packs\n• Zero watermarks and priority render pipeline",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
            )
          }
        }
      }

      // Section: Plugins & Extension Packs
      item {
        SettingsSection(title = "Plugins & Extension Packs") {
          // Action Banner
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Custom ZIP Plugin System", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                Text("Install custom filters, stickers, fonts, and title templates via ZIP", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
              }
              Button(
                onClick = {
                  try {
                    zipPickerLauncher.launch("*/*")
                  } catch (e: Exception) {
                    zipPickerLauncher.launch("application/zip")
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("upload_plugin_zip_button")
              ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload ZIP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }

            Divider(color = StudioBorder)

            // 1-Tap Sample Packs Installer
            Text("Sample Plugin Packs (1-Tap Test)", style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              val samplePacks = listOf(
                "Filters" to "filter",
                "Stickers" to "sticker",
                "Fonts" to "font",
                "Templates" to "text_template"
              )
              for ((label, sampleType) in samplePacks) {
                OutlinedButton(
                  onClick = {
                    when (val res = viewModel.installSamplePluginPack(sampleType)) {
                      is PluginValidationResult.Success -> {
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                      }
                      is PluginValidationResult.Error -> {
                        Toast.makeText(context, "Error: ${res.reason}", Toast.LENGTH_SHORT).show()
                      }
                    }
                  },
                  modifier = Modifier.weight(1f),
                  contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, StudioBorder)
                ) {
                  Text(label, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
              }
            }

            Divider(color = StudioBorder)

            // Filter Chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Installed Plugins (${installedPlugins.size})",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
              )
            }

            val categories = listOf("All", "Filters", "Stickers", "Fonts", "Templates")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(categories) { cat ->
                val isSel = selectedCategoryFilter == cat
                FilterChip(
                  selected = isSel,
                  onClick = { selectedCategoryFilter = cat },
                  label = { Text(cat, fontSize = 11.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                    selectedLabelColor = CyanAccent,
                    containerColor = StudioSurfaceVariant,
                    labelColor = TextSecondary
                  ),
                  border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSel,
                    borderColor = StudioBorder,
                    selectedBorderColor = CyanAccent
                  )
                )
              }
            }

            // Plugin List
            val filteredList = installedPlugins.filter { p ->
              if (selectedCategoryFilter == "All") true
              else p.manifest.category.displayName.contains(selectedCategoryFilter, ignoreCase = true)
            }

            if (filteredList.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (installedPlugins.isEmpty()) "No plugins installed yet. Tap 'Upload ZIP' or install a Sample Pack above!" else "No plugins found for category '$selectedCategoryFilter'.",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                  fontSize = 12.sp
                )
              }
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (plugin in filteredList) {
                  PluginCardItem(
                    plugin = plugin,
                    onToggleEnabled = { isChecked ->
                      viewModel.togglePluginEnabled(plugin.manifest.id, isChecked)
                    },
                    onUninstall = {
                      val uninstalled = viewModel.uninstallPlugin(plugin.manifest.id)
                      if (uninstalled) {
                        Toast.makeText(context, "Uninstalled '${plugin.manifest.name}'", Toast.LENGTH_SHORT).show()
                      }
                    }
                  )
                }
              }
            }
          }
        }
      }

      // Section: Editor Settings
      item {
        SettingsSection(title = "Editor Configuration") {
          SettingsSwitchRow(
            title = "Timeline Snapping",
            subtitle = "Magnetically snap playhead to cut points and clip borders",
            checked = settings.timelineSnapping,
            onCheckedChange = { StudioPreferencesManager.updateSnapping(it) }
          )
          Divider(color = StudioBorder)
          SettingsSwitchRow(
            title = "Proxy Preview Rendering",
            subtitle = "Uses lightweight 720p proxy frames during editing for extreme smoothness",
            checked = settings.previewQualityProxy,
            onCheckedChange = { StudioPreferencesManager.updateProxyMode(it) }
          )
          Divider(color = StudioBorder)
          SettingsInfoRow(
            title = "Auto-Save Interval",
            value = "${settings.autoSaveIntervalSec} seconds"
          )
        }
      }

      // Section: Storage & Cache
      item {
        SettingsSection(title = "Storage & Performance") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Temporary Render Cache", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium))
              Text("Current size: $cacheSizeText", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
            Button(
              onClick = {
                val cleared = StudioPreferencesManager.clearAppCache()
                cacheSizeText = "0.0 MB"
                Toast.makeText(context, "Cleared 48.5 MB of temporary cache!", Toast.LENGTH_SHORT).show()
              },
              colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = TextPrimary),
              shape = RoundedCornerShape(14.dp)
            ) {
              Text("Clear Cache", fontSize = 12.sp)
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(40.dp)) }
    }
  }
}

@Composable
private fun PluginCardItem(
  plugin: InstalledPlugin,
  onToggleEnabled: (Boolean) -> Unit,
  onUninstall: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
    border = BorderStroke(1.dp, if (plugin.isEnabled) CyanAccent.copy(alpha = 0.5f) else StudioBorder)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = PurpleAccent.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, PurpleAccent)
          ) {
            Text(
              text = plugin.manifest.category.displayName,
              style = MaterialTheme.typography.labelSmall.copy(color = PurpleAccent, fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Text(
            text = plugin.manifest.name,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
            checked = plugin.isEnabled,
            onCheckedChange = onToggleEnabled,
            colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = StudioBorder)
          )
          IconButton(onClick = onUninstall, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Uninstall Plugin", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
          }
        }
      }

      Text(
        text = plugin.manifest.description.ifBlank { "Custom extension package with ${plugin.manifest.items.size} assets." },
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Author: ${plugin.manifest.author}", fontSize = 10.sp, color = TextSecondary)
        Text("Version v${plugin.manifest.version} • ${plugin.manifest.items.size} assets", fontSize = 10.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun SettingsSection(
  title: String,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
    )
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = StudioSurface),
      border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.4f))))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
      )
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium))
      Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = StudioBorder)
    )
  }
}

@Composable
private fun SettingsInfoRow(
  title: String,
  value: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
    Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
  }
}

