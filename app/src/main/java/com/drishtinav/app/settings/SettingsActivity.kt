package com.drishtinav.app.settings

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Big-text, TalkBack-friendly settings. Everything applies immediately
 * and is re-read by MainActivity in onResume.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(titleView("Settings"))

        // --- Speech rate ---
        val rateLabel = labelView()
        root.addView(rateLabel)
        root.addView(seekBar(80, 150, (settings.speechRate * 100).toInt()) { progress ->
            settings.speechRate = progress / 100f
            rateLabel.text = rateText(progress / 100f)
        }.also { rateLabel.text = rateText(settings.speechRate) })

        // --- Guidance language ---
        root.addView(
            switchRow(
                "Hindi guidance",
                settings.speechLanguage == AppSettings.LANG_HI,
                "Off: English. Needs the Hindi voice in Text-to-speech settings"
            ) {
                settings.speechLanguage = if (it) AppSettings.LANG_HI else AppSettings.LANG_EN
            }
        )

        // --- Urgent distance ---
        val urgentLabel = labelView()
        root.addView(urgentLabel)
        root.addView(seekBar(50, 200, (settings.urgentDistanceM * 100).toInt()) { progress ->
            settings.urgentDistanceM = progress / 100f
            urgentLabel.text = urgentText(settings.urgentDistanceM)
        }.also { urgentLabel.text = urgentText(settings.urgentDistanceM) })

        // --- Near distance ---
        val nearLabel = labelView()
        root.addView(nearLabel)
        root.addView(seekBar(100, 400, (settings.nearDistanceM * 100).toInt()) { progress ->
            settings.nearDistanceM = progress / 100f
            // The setter clamps; re-read the real value for the label.
            nearLabel.text = nearText(settings.nearDistanceM)
        }.also { nearLabel.text = nearText(settings.nearDistanceM) })

        // --- Keep screen on ---
        root.addView(switchRow("Keep screen on while scanning", settings.keepScreenOn) {
            settings.keepScreenOn = it
        })

        // --- Side-zone near alerts ---
        root.addView(
            switchRow(
                "Announce side obstacles too",
                settings.announceSideNear,
                "Off: only obstacles in your walking path are announced"
            ) {
                settings.announceSideNear = it
            }
        )

        setContentView(root)
    }

    // ------------------------------------------------------------- ui helpers

    private fun titleView(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        setPadding(0, 0, 0, 32)
    }

    private fun labelView() = TextView(this).apply {
        textSize = 20f
        setPadding(0, 32, 0, 8)
    }

    private fun seekBar(min: Int, max: Int, progress: Int, onChange: (Int) -> Unit) =
        SeekBar(this).apply {
            this.min = min
            this.max = max
            this.progress = progress
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16 }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

    private fun switchRow(
        title: String,
        checked: Boolean,
        subtitle: String? = null,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }
        val toggle = Switch(this).apply {
            text = title
            textSize = 20f
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(toggle)
        subtitle?.let {
            row.addView(TextView(this).apply {
                text = it
                textSize = 15f
                setPadding(0, 4, 0, 0)
            })
        }
        return row
    }

    private fun rateText(rate: Float) =
        String.format(Locale.US, "Speech rate: %.2fx", rate)

    private fun urgentText(m: Float) =
        String.format(Locale.US, "Urgent distance: %.1f m (interrupts speech)", m)

    private fun nearText(m: Float) =
        String.format(Locale.US, "Near distance: %.1f m (announced ahead)", m)
}
