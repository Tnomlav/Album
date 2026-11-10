package com.example.album

/**
 * Third-party reverse-image search sites used by both the toolbox entries and
 * the single-image actions. Keeping the URL and host rules together prevents a
 * crafted result link from loading arbitrary pages inside Album's WebView.
 */
enum class ImageSearchSource(
    val key: String,
    val displayName: String,
    val startUrl: String,
    private val rootHost: String
) {
    SauceNao(
        key = "saucenao",
        displayName = "SauceNAO",
        startUrl = "https://saucenao.com/",
        rootHost = "saucenao.com"
    ),
    Ascii2d(
        key = "ascii2d",
        displayName = "ascii2d",
        startUrl = "https://ascii2d.net/",
        rootHost = "ascii2d.net"
    );

    fun allowsHost(host: String?): Boolean {
        val normalized = host?.trim()?.lowercase() ?: return false
        return normalized == rootHost || normalized.endsWith(".$rootHost")
    }

    companion object {
        fun fromKey(key: String?): ImageSearchSource =
            entries.firstOrNull { it.key == key } ?: SauceNao

        /**
         * The system share sheet offers one entry per site, declared as an
         * `activity-alias` in AndroidManifest.xml. Because an alias is just a
         * second component name for the same activity, the component that
         * launched us is what tells the browser which site to open.
         */
        fun fromShareComponent(className: String?): ImageSearchSource? = when {
            className == null -> null
            className.endsWith("ImageSearchSauceNao") -> SauceNao
            className.endsWith("ImageSearchAscii2d") -> Ascii2d
            else -> null
        }
    }
}
