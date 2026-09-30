package com.drishtinav.app.output

import com.drishtinav.app.perception.Direction
import java.util.Locale

/**
 * Every sentence the app speaks, in English and Hindi.
 *
 * Set [hindi] once from the live TTS state (MainActivity.applySettings);
 * all builders below then produce the active language. Object labels come
 * from the on-device model in English, so a COCO English→Hindi map is
 * included — without it, Hindi guidance would still say "chair".
 *
 * OSRM turn instructions arrive in English from the server. [maneuver]
 * translates the leading verb phrase ("Turn left" → "बाईं ओर मुड़ें");
 * street names stay as-is.
 */
object AppStrings {

    @Volatile
    var hindi: Boolean = false

    // ---------------------------------------------------------- obstacles

    fun obstacleAlert(
        labelEn: String,
        distanceMeters: Float?,
        direction: Direction,
        urgent: Boolean
    ): String {
        val label = label(labelEn)
        val dir = directionText(direction)
        val dist = distanceMeters?.let { formatShortMeters(it.toDouble()) }
        return if (hindi) {
            val prefix = if (urgent) "रुकिए। " else ""
            if (dist == null) "${prefix}सामने $label है।"
            else "$prefix$dir $dist पर $label है।"
        } else {
            val prefix = if (urgent) "Stop. " else ""
            if (dist == null) "$prefix$label, ahead."
            else "$prefix$label, $dist, $dir."
        }
    }

    private fun directionText(direction: Direction): String = when (direction) {
        Direction.LEFT -> if (hindi) "बाईं ओर" else "to your left"
        Direction.RIGHT -> if (hindi) "दाईं ओर" else "to your right"
        Direction.CENTER -> if (hindi) "सामने" else "ahead"
    }

    /** "1.5 meters" / "1.5 मीटर" — one decimal, for obstacle alerts. */
    fun formatShortMeters(meters: Double): String =
        if (hindi) String.format(Locale.US, "%.1f मीटर", meters)
        else String.format(Locale.US, "%.1f meters", meters)

    // ---------------------------------------------------------- navigation

    /** "50 meters" / "50 मीटर", "1.2 kilometers" / "1.2 किलोमीटर". */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            val m = meters.toInt() / 10 * 10
            if (hindi) "$m मीटर" else "$m meters"
        } else {
            if (hindi) String.format(Locale.US, "%.1f किलोमीटर", meters / 1000.0)
            else String.format(Locale.US, "%.1f kilometers", meters / 1000.0)
        }
    }

    fun routeFound(totalMeters: Double, firstInstructionEn: String?): String {
        val dist = formatDistance(totalMeters)
        val first = firstInstructionEn?.let { maneuver(it) } ?: ""
        return if (hindi) "रास्ता मिल गया, ${dist}। $first"
        else "Route found, $dist. $first"
    }

    fun approaching(distToManeuverM: Double, instructionEn: String): String {
        val dist = formatDistance(distToManeuverM)
        val instruction = maneuver(instructionEn)
        return if (hindi) "$dist में, ${instruction}।"
        else "In $dist, $instruction."
    }

    fun arrived(): String =
        if (hindi) "आप अपनी मंज़िल पर पहुँच गए हैं।"
        else "You have arrived at your destination."

    fun rerouting(): String =
        if (hindi) "रास्ता दोबारा बनाया जा रहा है।"
        else "Rerouting."

    fun navigationStopped(): String =
        if (hindi) "नेविगेशन बंद कर दिया गया।"
        else "Navigation stopped."

    fun routeFailed(): String =
        if (hindi) "रास्ता नहीं बन पाया। अपना इंटरनेट कनेक्शन जाँचें।"
        else "Could not plan a route. Check your internet connection."

    fun scanStarted(): String =
        if (hindi) "दृष्टि नैव शुरू हो गया। कैमरा सामने की ओर रखें।"
        else "Drishti nav started. Point the camera forward."

    // ---------------------------------------------------------- maneuvers
    // Longest prefixes first — "turn slight left" must win over "turn left".

    private val maneuverPrefixes = listOf(
        "turn sharp left" to "तेज़ी से बाईं ओर मुड़ें",
        "turn sharp right" to "तेज़ी से दाईं ओर मुड़ें",
        "turn slight left" to "हल्के बाईं ओर मुड़ें",
        "turn slight right" to "हल्के दाईं ओर मुड़ें",
        "make a u-turn" to "यू-टर्न लें",
        "make a uturn" to "यू-टर्न लें",
        "u-turn" to "यू-टर्न लें",
        "uturn" to "यू-टर्न लें",
        "take the roundabout" to "गोल चक्कर पार करें",
        "enter the roundabout" to "गोल चक्कर में प्रवेश करें",
        "exit the roundabout" to "गोल चक्कर से निकलें",
        "at the roundabout" to "गोल चक्कर पर",
        "turn left" to "बाईं ओर मुड़ें",
        "turn right" to "दाईं ओर मुड़ें",
        "keep left" to "बाईं ओर रहें",
        "keep right" to "दाईं ओर रहें",
        "continue straight" to "सीधे चलते रहें",
        "continue" to "सीधे चलते रहें",
        "head northeast" to "उत्तर-पूर्व की ओर जाएं",
        "head northwest" to "उत्तर-पश्चिम की ओर जाएं",
        "head southeast" to "दक्षिण-पूर्व की ओर जाएं",
        "head southwest" to "दक्षिण-पश्चिम की ओर जाएं",
        "head north" to "उत्तर की ओर जाएं",
        "head south" to "दक्षिण की ओर जाएं",
        "head east" to "पूर्व की ओर जाएं",
        "head west" to "पश्चिम की ओर जाएं",
        "you will arrive" to "आप पहुँचेंगे",
        "you have arrived" to "आप पहुँच गए हैं",
        "arrive" to "पहुँचें",
        "depart" to "यात्रा शुरू करें",
        "merge" to "आगे मिल जाएं"
    )

    /**
     * Translates the leading English verb phrase of an OSRM instruction.
     * Street names and the rest of the sentence stay as-is.
     */
    fun maneuver(instructionEn: String): String {
        if (!hindi) return instructionEn
        val lower = instructionEn.lowercase(Locale.US)
        for ((prefix, replacement) in maneuverPrefixes) {
            if (lower.startsWith(prefix)) {
                val rest = instructionEn.substring(prefix.length)
                    .replace(" onto ", " ", ignoreCase = true)
                    .replace(" on ", " ", ignoreCase = true)
                    .trim()
                return if (rest.isEmpty()) "${replacement}।" else "$replacement $rest।"
            }
        }
        return instructionEn
    }

    // ---------------------------------------------------------- COCO labels

    private val labelsHi = mapOf(
        "person" to "व्यक्ति",
        "bicycle" to "साइकिल",
        "car" to "कार",
        "motorcycle" to "मोटरसाइकिल",
        "airplane" to "हवाई जहाज",
        "bus" to "बस",
        "train" to "ट्रेन",
        "truck" to "ट्रक",
        "boat" to "नाव",
        "traffic light" to "ट्रैफिक लाइट",
        "fire hydrant" to "फायर हाइड्रेंट",
        "stop sign" to "स्टॉप साइन",
        "parking meter" to "पार्किंग मीटर",
        "bench" to "बेंच",
        "bird" to "पक्षी",
        "cat" to "बिल्ली",
        "dog" to "कुत्ता",
        "horse" to "घोड़ा",
        "sheep" to "भेड़",
        "cow" to "गाय",
        "elephant" to "हाथी",
        "bear" to "भालू",
        "zebra" to "ज़ेबरा",
        "giraffe" to "जिराफ़",
        "backpack" to "बैकपैक",
        "umbrella" to "छाता",
        "handbag" to "हैंडबैग",
        "tie" to "टाई",
        "suitcase" to "सूटकेस",
        "frisbee" to "फ्रिसबी",
        "skis" to "स्की",
        "snowboard" to "स्नोबोर्ड",
        "sports ball" to "खेल की गेंद",
        "kite" to "पतंग",
        "baseball bat" to "बेसबॉल बैट",
        "baseball glove" to "बेसबॉल दस्ताना",
        "skateboard" to "स्केटबोर्ड",
        "surfboard" to "सर्फबोर्ड",
        "tennis racket" to "टेनिस रैकेट",
        "bottle" to "बोतल",
        "wine glass" to "वाइन ग्लास",
        "cup" to "कप",
        "fork" to "कांटा",
        "knife" to "चाकू",
        "spoon" to "चम्मच",
        "bowl" to "कटोरा",
        "banana" to "केला",
        "apple" to "सेब",
        "sandwich" to "सैंडविच",
        "orange" to "संतरा",
        "broccoli" to "ब्रोकली",
        "carrot" to "गाजर",
        "hot dog" to "हॉट डॉग",
        "pizza" to "पिज़्ज़ा",
        "donut" to "डोनट",
        "cake" to "केक",
        "chair" to "कुर्सी",
        "couch" to "सोफ़ा",
        "potted plant" to "गमले का पौधा",
        "bed" to "बिस्तर",
        "dining table" to "खाने की मेज़",
        "toilet" to "टॉयलेट",
        "tv" to "टीवी",
        "laptop" to "लैपटॉप",
        "mouse" to "माउस",
        "remote" to "रिमोट",
        "keyboard" to "कीबोर्ड",
        "cell phone" to "मोबाइल फ़ोन",
        "microwave" to "माइक्रोवेव",
        "oven" to "ओवन",
        "toaster" to "टोस्टर",
        "sink" to "सिंक",
        "refrigerator" to "फ्रिज",
        "book" to "किताब",
        "clock" to "घड़ी",
        "vase" to "फूलदान",
        "scissors" to "कैंची",
        "teddy bear" to "टेडी बियर",
        "hair drier" to "हेयर ड्रायर",
        "toothbrush" to "टूथब्रश"
    )

    /** Model labels are English; translate when Hindi guidance is active. */
    fun label(labelEn: String): String =
        if (hindi) labelsHi[labelEn.lowercase(Locale.US)] ?: labelEn else labelEn
}
