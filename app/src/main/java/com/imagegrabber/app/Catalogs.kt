package com.imagegrabber.app

/** لون: قيمته لكل مصدر (null = المصدر لا يدعمه فيُتجاهل). Pexels يقبل أي لون HEX. */
data class ColorOpt(
    val id: String, val label: String, val swatch: Long,
    val pexels: String?, val pixabay: String?, val unsplash: String?
)

object ColorCatalog {
    val all = listOf(
        ColorOpt("red", "أحمر", 0xFFE53935, "red", "red", "red"),
        ColorOpt("orange", "برتقالي", 0xFFFB8C00, "orange", "orange", "orange"),
        ColorOpt("yellow", "أصفر", 0xFFFDD835, "yellow", "yellow", "yellow"),
        ColorOpt("green", "أخضر", 0xFF43A047, "green", "green", "green"),
        ColorOpt("turquoise", "تركواز", 0xFF26C6DA, "turquoise", "turquoise", "teal"),
        ColorOpt("blue", "أزرق", 0xFF1E88E5, "blue", "blue", "blue"),
        ColorOpt("violet", "بنفسجي", 0xFF8E24AA, "violet", "lilac", "purple"),
        ColorOpt("pink", "وردي", 0xFFEC407A, "pink", "pink", "magenta"),
        ColorOpt("brown", "بني", 0xFF6D4C41, "brown", "brown", null),
        ColorOpt("black", "أسود", 0xFF212121, "black", "black", "black"),
        ColorOpt("gray", "رمادي", 0xFF9E9E9E, "gray", "gray", "black_and_white"),
        ColorOpt("white", "أبيض", 0xFFFAFAFA, "white", "white", "white"),
        ColorOpt("grayscale", "أبيض وأسود", 0xFF616161, null, "grayscale", "black_and_white"),
        ColorOpt("gold", "ذهبي", 0xFFFFD700, "#FFD700", "yellow", "yellow"),
        ColorOpt("navy", "كحلي", 0xFF000080, "#000080", "blue", "blue"),
        ColorOpt("maroon", "عنابي", 0xFF800000, "#800000", "red", "red"),
        ColorOpt("beige", "بيج", 0xFFF5F5DC, "#F5F5DC", "brown", null),
        ColorOpt("cream", "كريمي", 0xFFFFFDD0, "#FFFDD0", "white", "white"),
        ColorOpt("cyan", "سماوي", 0xFF00FFFF, "#00FFFF", "turquoise", "teal"),
        ColorOpt("skyblue", "أزرق فاتح", 0xFF87CEEB, "#87CEEB", "blue", "blue"),
        ColorOpt("lime", "ليموني", 0xFF32CD32, "#32CD32", "green", "green"),
        ColorOpt("olive", "زيتي", 0xFF808000, "#808000", "green", "green"),
        ColorOpt("mint", "نعناعي", 0xFF98FF98, "#98FF98", "green", "green"),
        ColorOpt("teal", "أخضر مزرق", 0xFF008080, "#008080", "turquoise", "teal"),
        ColorOpt("purple", "أرجواني", 0xFF800080, "#800080", "lilac", "purple"),
        ColorOpt("lavender", "لافندر", 0xFFE6E6FA, "#E6E6FA", "lilac", "purple"),
        ColorOpt("magenta", "فوشي", 0xFFFF00FF, "#FF00FF", "pink", "magenta"),
        ColorOpt("coral", "مرجاني", 0xFFFF7F50, "#FF7F50", "orange", "orange"),
        ColorOpt("silver", "فضي", 0xFFC0C0C0, "#C0C0C0", "gray", null)
    )

    fun get(id: String?): ColorOpt? = if (id == null) null else all.firstOrNull { it.id == id }
}

/** الخلفية والأسلوب: تُضاف ككلمات إنجليزية لنص البحث (تعمل مع كل المصادر). */
object Styles {
    val backgrounds = listOf(
        "transparent" to "شفافة", "white" to "بيضاء", "black" to "سوداء", "plain" to "سادة",
        "gradient" to "متدرجة", "bokeh" to "ضبابية (بوكيه)", "pattern" to "نمط متكرر",
        "texture" to "خامة / ملمس", "wallpaper" to "خلفية شاشة", "abstract" to "تجريدية",
        "sky" to "سماء", "studio" to "استوديو"
    )
    private val bgWords = mapOf(
        "transparent" to "transparent background png", "white" to "white background",
        "black" to "black background", "plain" to "plain solid background",
        "gradient" to "gradient background", "bokeh" to "blurred bokeh background",
        "pattern" to "seamless pattern", "texture" to "texture background",
        "wallpaper" to "wallpaper hd", "abstract" to "abstract background",
        "sky" to "sky background", "studio" to "studio backdrop"
    )

    val styles = listOf(
        "3d" to "ثلاثي الأبعاد", "icon" to "أيقونة", "logo" to "شعار", "painting" to "لوحة زيتية",
        "watercolor" to "ألوان مائية", "sketch" to "رسم بالقلم", "cartoon" to "كرتون", "anime" to "أنمي",
        "pixel" to "بيكسل آرت", "flat" to "تصميم مسطح", "infographic" to "إنفوجرافيك", "map" to "خريطة",
        "aerial" to "منظر جوي", "macro" to "ماكرو", "bw" to "أبيض وأسود", "minimal" to "بسيط (مينيمال)",
        "vintage" to "قديم (فينتج)", "neon" to "نيون", "dark" to "داكن", "silhouette" to "ظل (سيلويت)",
        "isolated" to "معزول"
    )
    private val styleWords = mapOf(
        "3d" to "3d render", "icon" to "icon", "logo" to "logo", "painting" to "oil painting",
        "watercolor" to "watercolor", "sketch" to "pencil sketch", "cartoon" to "cartoon", "anime" to "anime",
        "pixel" to "pixel art", "flat" to "flat design", "infographic" to "infographic", "map" to "map",
        "aerial" to "aerial view", "macro" to "macro", "bw" to "black and white", "minimal" to "minimalist",
        "vintage" to "vintage", "neon" to "neon", "dark" to "dark moody", "silhouette" to "silhouette",
        "isolated" to "isolated"
    )

    fun extras(f: SearchFilters): String =
        listOfNotNull(f.style?.let { styleWords[it] }, f.background?.let { bgWords[it] }).joinToString(" ")

    fun build(q: String, f: SearchFilters): String = (q.trim() + " " + extras(f)).trim()
}
