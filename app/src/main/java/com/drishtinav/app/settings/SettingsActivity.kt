package com.drishtinav.app.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drishtinav.app.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.util.Locale

/**
 * Big-text, TalkBack-friendly settings in grouped Material 3 sections.
 * Everything applies immediately and is re-read by MainActivity in onResume.
 *
 * Sections: Speech (voice rate + language), Detection (urgent/near
 * distances), Alerts (screen + side-obstacle announcements).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)

        val root = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        root.addView(column)

        // Header.
        column.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            isAccessibilityHeading = true
        })
        column.addView(TextView(this).apply {
            text = getString(R.string.settings_subtitle)
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            setPadding(0, dp(4), 0, dp(4))
        })

        // --- Speech section ---
        column.addView(sectionCard(R.drawable.ic_section_speech, getString(R.string.section_speech)) {
            val rateValue = TextView(this@SettingsActivity)
            addView(sliderRow(
                label = getString(R.string.speech_rate_label),
                contentDesc = getString(R.string.speech_rate_desc),
                valueText = rateValue,
                min = 0.8f, max = 1.5f, step = 0.05f,
                initial = settings.speechRate,
                format = { v -> String.format(Locale.US, "%.2fx", v) },
                onChange = { v ->
                    settings.speechRate = v
                    rateValue.text = String.format(Locale.US, "%.2fx", v)
                }
            ))
            addView(divider())
            addView(switchRow(
                title = getString(R.string.hindi_guidance),
                subtitle = getString(R.string.hindi_guidance_sub),
                checked = settings.speechLanguage == AppSettings.LANG_HI
            ) {
                settings.speechLanguage = if (it) AppSettings.LANG_HI else AppSettings.LANG_EN
            })
        })

        // --- Detection section ---
        column.addView(sectionCard(R.drawable.ic_section_radar, getString(R.string.section_detection)) {
            val urgentValue = TextView(this@SettingsActivity)
            addView(sliderRow(
                label = getString(R.string.urgent_distance_label),
                contentDesc = getString(R.string.urgent_distance_desc),
                valueText = urgentValue,
                min = 0.5f, max = 2.0f, step = 0.1f,
                initial = settings.urgentDistanceM,
                format = { v -> String.format(Locale.US, "%.1f m", v) },
                onChange = { v ->
                    settings.urgentDistanceM = v
                    urgentValue.text = String.format(Locale.US, "%.1f m", settings.urgentDistanceM)
                }
            ))
            addView(divider())
            val nearValue = TextView(this@SettingsActivity)
            addView(sliderRow(
                label = getString(R.string.near_distance_label),
                contentDesc = getString(R.string.near_distance_desc),
                valueText = nearValue,
                min = 1.0f, max = 4.0f, step = 0.1f,
                initial = settings.nearDistanceM,
                format = { v -> String.format(Locale.US, "%.1f m", v) },
                onChange = { v ->
                    settings.nearDistanceM = v
                    // The setter clamps; re-read the real value for the label.
                    nearValue.text = String.format(Locale.US, "%.1f m", settings.nearDistanceM)
                }
            ))
        })

        // --- Alerts section ---
        column.addView(sectionCard(R.drawable.ic_section_bell, getString(R.string.section_alerts)) {
            addView(switchRow(
                title = getString(R.string.keep_screen_on),
                subtitle = null,
                checked = settings.keepScreenOn
            ) {
                settings.keepScreenOn = it
            })
            addView(divider())
            addView(switchRow(
                title = getString(R.string.announce_side),
                subtitle = getString(R.string.announce_side_sub),
                checked = settings.announceSideNear
            ) {
                settings.announceSideNear = it
            })
        })

        setContentView(root)
    }

    // ------------------------------------------------------------- ui helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun onSurface() = ContextCompat.getColor(this, R.color.on_surface)
    private fun onSurfaceVariant() = ContextCompat.getColor(this, R.color.on_surface_variant)

    /** Card with an icon + section title header; [build] adds rows below the header. */
    private fun sectionCard(
        iconRes: Int,
        title: String,
        build: LinearLayout.() -> Unit
    ): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_container))
            strokeColor = ContextCompat.getColor(context, R.color.outline_variant)
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(16) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val icon = android.widget.ImageView(this).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            contentDescription = null // decorative; header text carries meaning
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface())
            setPadding(dp(12), 0, 0, 0)
            isAccessibilityHeading = true
        }
        header.addView(icon)
        header.addView(titleView)
        inner.addView(header)
        inner.build()
        card.addView(inner)
        return card
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).also { it.topMargin = dp(8); it.bottomMargin = dp(8) }
        setBackgroundColor(ContextCompat.getColor(context, R.color.outline_variant))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun sliderRow(
        label: String,
        contentDesc: String,
        valueText: TextView,
        min: Float,
        max: Float,
        step: Float,
        initial: Float,
        format: (Float) -> String,
        onChange: (Float) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(6))
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 17f
            setTextColor(onSurface())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        valueText.apply {
            text = format(initial)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.teal))
        }
        topRow.addView(labelView)
        topRow.addView(valueText)
        row.addView(topRow)

        val slider = Slider(this).apply {
            valueFrom = min
            valueTo = max
            stepSize = step
            value = initial.coerceIn(min, max)
            // Large touch target for low-vision users.
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            )
            contentDescription = contentDesc
            addOnChangeListener { _, v, fromUser ->
                if (fromUser) onChange(v)
            }
        }
        row.addView(slider)
        return row
    }

    private fun switchRow(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val toggle = MaterialSwitch(this).apply {
            text = title
            textSize = 17f
            setTextColor(onSurface())
            isChecked = checked
            // 56dp+ touch target for low-vision users.
            minimumHeight = dp(56)
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(toggle)
        subtitle?.let {
            row.addView(TextView(this).apply {
                text = it
                textSize = 14f
                setTextColor(onSurfaceVariant())
                setPadding(dp(4), dp(2), 0, 0)
            })
        }
        return row
    }
}
