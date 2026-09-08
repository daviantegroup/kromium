package dev.daviante.kromium.presentation.menu

import org.cef.callback.CefContextMenuParams

/**
 * Encapsulates the contextual parameters of the user's right-click or context menu gesture.
 *
 * Provides convenient access to clicked coordinates, hyperlinks, media URLs, and selected text.
 *
 * @property x Horizontal coordinate relative to the browser viewport where right-click occurred.
 * @property y Vertical coordinate relative to the browser viewport where right-click occurred.
 * @property linkUrl The target URL if the right-click occurred on a hyperlink, or null otherwise.
 * @property unfilteredLinkUrl The unfiltered target URL if on a hyperlink, or null otherwise.
 * @property sourceUrl The source URL if right-click occurred on media (image, audio, video), or null.
 * @property hasImage True if the clicked element has image contents.
 * @property pageUrl URL of the top-level page where the context menu was triggered.
 * @property frameUrl URL of the specific subframe where the context menu was triggered.
 * @property selectionText Any text currently highlighted/selected by the user, or null.
 * @property misspelledWord The misspelled word under cursor if spellcheck is active, or null.
 * @property isEditable True if the context menu was triggered inside an editable field (input, textarea).
 * @property isSpellCheckEnabled True if spell checking is enabled for the clicked context.
 */
data class KromiumContextMenuParams(
    val x: Int,
    val y: Int,
    val linkUrl: String?,
    val unfilteredLinkUrl: String?,
    val sourceUrl: String?,
    val hasImage: Boolean,
    val pageUrl: String?,
    val frameUrl: String?,
    val selectionText: String?,
    val misspelledWord: String?,
    val isEditable: Boolean,
    val isSpellCheckEnabled: Boolean
) {
    /** True if text is currently highlighted / selected. */
    fun hasSelection(): Boolean = !selectionText.isNullOrBlank()

    /** True if the click was on a hyperlink. */
    fun isLink(): Boolean = !linkUrl.isNullOrBlank()

    /** True if the click was on media or an image element. */
    fun hasMedia(): Boolean = hasImage || !sourceUrl.isNullOrBlank()

    companion object {
        /**
         * Constructs a [KromiumContextMenuParams] instance from raw JCEF [CefContextMenuParams].
         */
        @JvmStatic
        fun from(params: CefContextMenuParams?): KromiumContextMenuParams {
            if (params == null) {
                return KromiumContextMenuParams(
                    x = 0,
                    y = 0,
                    linkUrl = null,
                    unfilteredLinkUrl = null,
                    sourceUrl = null,
                    hasImage = false,
                    pageUrl = null,
                    frameUrl = null,
                    selectionText = null,
                    misspelledWord = null,
                    isEditable = false,
                    isSpellCheckEnabled = false
                )
            }
            return KromiumContextMenuParams(
                x = params.xCoord,
                y = params.yCoord,
                linkUrl = params.linkUrl?.takeIf { it.isNotEmpty() },
                unfilteredLinkUrl = params.unfilteredLinkUrl?.takeIf { it.isNotEmpty() },
                sourceUrl = params.sourceUrl?.takeIf { it.isNotEmpty() },
                hasImage = params.hasImageContents(),
                pageUrl = params.pageUrl?.takeIf { it.isNotEmpty() },
                frameUrl = params.frameUrl?.takeIf { it.isNotEmpty() },
                selectionText = params.selectionText?.takeIf { it.isNotEmpty() },
                misspelledWord = params.misspelledWord?.takeIf { it.isNotEmpty() },
                isEditable = params.isEditable,
                isSpellCheckEnabled = params.isSpellCheckEnabled
            )
        }
    }
}
