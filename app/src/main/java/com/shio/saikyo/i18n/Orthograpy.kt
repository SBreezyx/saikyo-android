package com.shio.saikyo.i18n


// TODO: put this information into a database

enum class TextLayout {
    LTR, RTL
}

enum class TextFlow {
    VERTICAL, HORIZONTAL
}

enum class LangCode {
    en,
    ja,
}

enum class Whitespace {
    NEWLINE,
    SEPARATOR,
}

val orthographies = mapOf(
    LangCode.en to mapOf(
        TextFlow.HORIZONTAL to TextLayout.LTR,
        TextFlow.VERTICAL to TextLayout.LTR,
        Whitespace.SEPARATOR to " ",
        Whitespace.NEWLINE to "\n",
    ),
    LangCode.ja to mapOf(
        TextFlow.HORIZONTAL to TextLayout.LTR,
        TextFlow.VERTICAL to TextLayout.RTL,
        Whitespace.SEPARATOR to "",
        Whitespace.NEWLINE to "\n",
    )
)