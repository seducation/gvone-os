package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.ui.theme.*

enum class ReaderTheme(val bg: Color, val text: Color, val nameLabel: String) {
    CHARCOAL(Color(0xFF131822), Color(0xFFE2E8F0), "Dark"),
    SEPIA(Color(0xFF2C241D), Color(0xFFE6D6C6), "Sepia"),
    CREAM(Color(0xFFFBF0D9), Color(0xFF2D241E), "Cream"),
    PURE_BLACK(Color(0xFF000000), Color(0xFFD4D4D8), "Black")
}

enum class ReaderFont(val label: String, val fontFamily: FontFamily) {
    SAN_FRANCISCO("San Francisco", FontFamily.SansSerif),
    NEW_YORK("New York Serif", FontFamily.Serif),
    MONO("Mono", FontFamily.Monospace)
}

@Composable
fun ReaderModeOverlay(
    tab: BrowserTab?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTheme by remember { mutableStateOf(ReaderTheme.CHARCOAL) }
    var selectedFont by remember { mutableStateOf(ReaderFont.NEW_YORK) }
    var fontSize by remember { mutableIntStateOf(17) }
    var showFormatMenu by remember { mutableStateOf(false) }

    val domain = try {
        val parsed = java.net.URI(tab?.url ?: "").host
        if (!parsed.isNullOrBlank()) parsed.removePrefix("www.") else "Safari Reader"
    } catch (e: Exception) {
        "Safari Reader"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(selectedTheme.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("safari_reader_mode_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Reader Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Done button
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.text.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Done", color = selectedTheme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                // Domain Indicator
                Text(
                    text = domain,
                    color = selectedTheme.text.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                // Formatting "aA" Button
                IconButton(
                    onClick = { showFormatMenu = !showFormatMenu },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(selectedTheme.text.copy(alpha = 0.12f))
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("a", color = selectedTheme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("A", color = selectedTheme.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Reader Theme & Typography Popover
            if (showFormatMenu) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selectedTheme == ReaderTheme.CREAM) Color(0xFFF3E5CA) else Color(0xFF1E2636),
                    border = androidx.compose.foundation.BorderStroke(1.dp, selectedTheme.text.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Font Size Slider: [ A- | A+ ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { if (fontSize > 13) fontSize -= 2 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.text.copy(alpha = 0.1f))
                            ) {
                                Text("A-", color = selectedTheme.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${fontSize}pt", color = selectedTheme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Button(
                                onClick = { if (fontSize < 28) fontSize += 2 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.text.copy(alpha = 0.1f))
                            ) {
                                Text("A+", color = selectedTheme.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Theme Swatches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ReaderTheme.values().forEach { theme ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(theme.bg)
                                        .border(
                                            width = if (selectedTheme == theme) 2.5.dp else 1.dp,
                                            color = if (selectedTheme == theme) GVONEPrimary else Color(0x33FFFFFF),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedTheme = theme }
                                )
                            }
                        }

                        // Font Family Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderFont.values().forEach { font ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedFont == font) GVONEPrimary.copy(alpha = 0.2f) else selectedTheme.text.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedFont == font) GVONEPrimary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedFont = font }
                                ) {
                                    Text(
                                        text = font.label,
                                        color = selectedTheme.text,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Article Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 32.dp)
            ) {
                Text(
                    text = if (tab?.title.isNullOrBlank() || tab?.title == "New Tab") "Physicists Reveal a Quantum Geometry Behind Fundamental Particles" else tab?.title!!,
                    color = selectedTheme.text,
                    fontSize = (fontSize + 6).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = selectedFont.fontFamily,
                    lineHeight = (fontSize + 12).sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "By Charlie Wood · Quanta Magazine · 6 min read",
                    color = selectedTheme.text.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontFamily = selectedFont.fontFamily
                )

                Divider(
                    color = selectedTheme.text.copy(alpha = 0.15f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Text(
                    text = "In modern theoretical physics, quantum field theories describe how elementary particles interact through fundamental forces. Recent research shows that the intricate mathematical structure of quantum amplitudes can be understood geometrically.",
                    color = selectedTheme.text,
                    fontSize = fontSize.sp,
                    fontFamily = selectedFont.fontFamily,
                    lineHeight = (fontSize * 1.6f).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Rather than calculating billions of traditional Feynman diagrams, physicists discovered that particle interactions form geometric volumes termed 'amplituhedrons'. Within this multidimensional space, calculating the volume of the object directly produces the scattering probability of gluons and quarks.",
                    color = selectedTheme.text,
                    fontSize = fontSize.sp,
                    fontFamily = selectedFont.fontFamily,
                    lineHeight = (fontSize * 1.6f).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "\"It feels as if we are peering into the deep code of spacetime itself,\" remarked one of the lead researchers. \"The principles of locality and quantum unitarity emerge naturally from the geometric facets.\"",
                    color = selectedTheme.text,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = selectedFont.fontFamily,
                    lineHeight = (fontSize * 1.6f).sp
                )
            }
        }
    }
}
