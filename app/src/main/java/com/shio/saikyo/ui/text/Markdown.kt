package com.shio.saikyo.ui.text

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.shio.saikyo.util.forEach
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private fun ASTNode.toString(fileTxt: CharSequence): String = getTextInNode(fileTxt).toString()

object Markdown {
    val DefaultParser = MarkdownParser(CommonMarkFlavourDescriptor())
}

// TODO: annotated strings do not work with TextView -- decide what kind of text primitive to use and rewrite for it
fun renderAsMarkdown(md: String, parser: MarkdownParser = Markdown.DefaultParser) = buildAnnotatedString {
    parser.buildMarkdownTreeFromString(md).accept(
        object : RecursiveVisitor() {
            val self = this
            val sb = this@buildAnnotatedString

            // TODO: optimise markdown rendering using maps and keys, not when() {}
            override fun visitNode(node: ASTNode) {
                when (node.type) {
                    MarkdownElementTypes.STRONG -> sb.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        node.accept(self)
                    }

                    MarkdownElementTypes.UNORDERED_LIST -> {
                        for (child in node.children) {
                            sb.append("• ")
                            child.children.forEach(1) { child.accept(self) }
                        }
                    }

                    MarkdownElementTypes.ORDERED_LIST -> {
                        for ((ix, child) in node.children.withIndex()) {
                            sb.append("${ix + 1}.")
                            child.children.forEach(1) { child.accept(self) }
                        }
                    }

                    MarkdownElementTypes.ATX_1 -> sb.withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                        super.visitNode(node.children[1])
                    }

                    MarkdownElementTypes.ATX_2 -> sb.withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)) {
                        super.visitNode(node.children[1])
                    }

                    MarkdownElementTypes.ATX_3 -> sb.withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)) {
                        super.visitNode(node.children[1])
                    }

                    MarkdownElementTypes.ATX_4 -> sb.withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) {
                        super.visitNode(node.children[1])
                    }

                    MarkdownElementTypes.ATX_5 -> sb.withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)) {
                        super.visitNode(node.children[1])
                    }

                    MarkdownElementTypes.ATX_6 -> sb.withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)) {
                        super.visitNode(node.children[1])
                    }

                    MarkdownElementTypes.MARKDOWN_FILE -> {
                        super.visitNode(node)
                    }

                    else -> {
                        sb.append(node.toString(md))
                    }
                }
            }
        }
    )
}
