package com.example.data.webwidget

import android.graphics.Bitmap
import com.example.data.model.WebWidgetCropBounds
import com.example.data.model.WebWidgetInteraction
import com.example.data.model.WebWidgetType

data class DetectedDomElement(
    val tagName: String,
    val selector: String,
    val heading: String,
    val rectLeft: Float,
    val rectTop: Float,
    val rectWidth: Float,
    val rectHeight: Float,
    val suggestedType: WebWidgetType,
    val isCleanDom: Boolean,
    val html: String
)

data class WebWidgetSelectionDraft(
    val title: String,
    val sourceUrl: String,
    val siteName: String,
    val faviconUrl: String?,
    val widgetType: WebWidgetType,
    val domSelector: String?,
    val domTagName: String?,
    val extractedHtml: String?,
    val snapshotBitmap: Bitmap?,
    val snapshotBase64: String?,
    val cropBounds: WebWidgetCropBounds,
    val detectedElement: DetectedDomElement?,
    val refreshIntervalMinutes: Int = 15,
    val interactionMode: WebWidgetInteraction = WebWidgetInteraction.OPEN_ORIGINAL,
    val targetEnvironmentId: String = "personal"
)
