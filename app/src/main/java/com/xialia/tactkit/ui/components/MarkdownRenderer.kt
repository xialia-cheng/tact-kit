package com.xialia.tactkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.ui.theme.TactKitMono
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private object MarkdownParserHolder {
  val parser: Parser =
    Parser.builder()
      .extensions(listOf(TablesExtension.create(), TaskListItemsExtension.create()))
      .build()
}

@Composable
fun MarkdownRenderer(
  markdown: String,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val document =
    remember(markdown) { MarkdownParserHolder.parser.parse(markdown) as Document }

  Column(
    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    val children = collectChildren(document)
    val visibleChildren = if (compact) children.take(5) else children
    visibleChildren.forEach { node -> RenderBlockNode(node = node, compact = compact) }
  }
}

@Composable
private fun RenderBlockNode(node: Node, compact: Boolean) {
  when (node) {
    is Heading -> {
      val style =
        when (node.level) {
          1 -> MaterialTheme.typography.titleLarge
          2 -> MaterialTheme.typography.titleMedium
          else -> MaterialTheme.typography.titleSmall
        }.copy(fontWeight = FontWeight.Bold)
      val inline = buildInlineAnnotatedString(node)
      Text(
        text = inline,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (compact) 2 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
      )
    }

    is Paragraph -> {
      val inline = buildInlineAnnotatedString(node)
      Text(
        text = inline,
        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (compact) 4 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
      )
    }

    is BulletList -> {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        collectChildren(node).forEach { child ->
          if (child is ListItem) {
            RenderListItem(item = child, orderedIndex = null, compact = compact)
          }
        }
      }
    }

    is OrderedList -> {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        collectChildren(node).forEachIndexed { index, child ->
          if (child is ListItem) {
            RenderListItem(
              item = child,
              orderedIndex = node.markerStartNumber + index,
              compact = compact,
            )
          }
        }
      }
    }

    is FencedCodeBlock -> {
      RenderCodeBlock(
        code = node.literal.trimEnd('\n'),
        language = node.info.trim(),
        compact = compact,
      )
    }

    is IndentedCodeBlock -> {
      RenderCodeBlock(
        code = node.literal.trimEnd('\n'),
        language = "",
        compact = compact,
      )
    }

    is BlockQuote -> {
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      ) {
        Box(
          modifier =
            Modifier.width(3.dp)
              .fillMaxHeight()
              .clip(RoundedCornerShape(2.dp))
              .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          collectChildren(node).forEach { child -> RenderBlockNode(child, compact) }
        }
      }
    }

    is TableBlock -> {
      RenderGfmTable(table = node)
    }

    is ThematicBreak -> {
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun RenderListItem(item: ListItem, orderedIndex: Int?, compact: Boolean) {
  val children = collectChildren(item)
  val taskMarker = children.firstOrNull { it is TaskListItemMarker } as? TaskListItemMarker
  val contentNodes = children.filterNot { it is TaskListItemMarker }

  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    if (taskMarker != null) {
      Icon(
        imageVector =
          if (taskMarker.isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
        contentDescription = null,
        tint =
          if (taskMarker.isChecked) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        modifier = Modifier.padding(top = 2.dp).size(16.dp),
      )
    } else {
      Text(
        text = if (orderedIndex != null) "$orderedIndex." else "•",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.weight(1f),
    ) {
      contentNodes.forEach { child ->
        if (child is Paragraph && taskMarker?.isChecked == true) {
          Text(
            text = buildInlineAnnotatedString(child),
            style =
              (if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                .copy(textDecoration = TextDecoration.LineThrough),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          RenderBlockNode(child, compact)
        }
      }
    }
  }
}

@Composable
private fun RenderCodeBlock(code: String, language: String, compact: Boolean) {
  val keywordColor = MaterialTheme.colorScheme.primary
  val stringColor = MaterialTheme.colorScheme.tertiary
  val commentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
  val baseColor = MaterialTheme.colorScheme.onSurface

  val highlighted =
    remember(code, keywordColor, stringColor, commentColor, baseColor) {
      highlightCode(code, keywordColor, stringColor, commentColor, baseColor)
    }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      if (language.isNotEmpty() && !compact) {
        Text(
          text = language.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
      Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Text(
          text = highlighted,
          style =
            MaterialTheme.typography.bodySmall.copy(
              fontFamily = TactKitMono,
              lineHeight = 18.sp,
            ),
          maxLines = if (compact) 4 else Int.MAX_VALUE,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun RenderGfmTable(table: TableBlock) {
  val rows = mutableListOf<Pair<Boolean, List<TableCell>>>()
  collectChildren(table).forEach { section ->
    val isHead = section is TableHead
    if (section is TableHead || section is TableBody) {
      collectChildren(section).forEach { rowNode ->
        if (rowNode is TableRow) {
          val cells = collectChildren(rowNode).filterIsInstance<TableCell>()
          rows += isHead to cells
        }
      }
    }
  }
  if (rows.isEmpty()) return

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(6.dp)) {
      rows.forEachIndexed { idx, (isHead, cells) ->
        Row(
          modifier =
            Modifier.background(
                color =
                  if (isHead) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                  } else if (idx % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.45f)
                  } else {
                    Color.Transparent
                  },
                shape = RoundedCornerShape(8.dp),
              )
              .padding(vertical = 6.dp, horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          cells.forEach { cell ->
            Text(
              text = buildInlineAnnotatedString(cell),
              style = MaterialTheme.typography.bodySmall,
              fontWeight = if (isHead) FontWeight.Bold else FontWeight.Normal,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.width(110.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun buildInlineAnnotatedString(node: Node): AnnotatedString {
  val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
  val codeFg = MaterialTheme.colorScheme.primary
  val linkColor = MaterialTheme.colorScheme.primary

  return remember(node, codeBg, codeFg, linkColor) {
    buildAnnotatedString {
      fun appendChildren(parent: Node) {
        var child = parent.firstChild
        while (child != null) {
          when (child) {
            is Text -> append(child.literal)
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is Code -> {
              withStyle(
                SpanStyle(
                  fontFamily = FontFamily.Monospace,
                  background = codeBg,
                  color = codeFg,
                )
              ) {
                append(" ${child.literal} ")
              }
            }
            is StrongEmphasis -> {
              withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendChildren(child) }
            }
            is Emphasis -> {
              withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendChildren(child) }
            }
            is Link -> {
              withStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
              ) {
                appendChildren(child)
              }
            }
            else -> appendChildren(child)
          }
          child = child.next
        }
      }
      appendChildren(node)
    }
  }
}

private fun highlightCode(
  code: String,
  keywordColor: Color,
  stringColor: Color,
  commentColor: Color,
  baseColor: Color,
): AnnotatedString {
  val keywords =
    Regex(
      "\\b(fun|val|var|class|object|interface|return|if|else|when|for|while|import|package|suspend|data|sealed|const|def|function|let|async|await|SELECT|FROM|WHERE)\\b"
    )
  val strings = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
  val comments = Regex("//.*|#.*")

  return buildAnnotatedString {
    code.lines().forEachIndexed { lineIdx, line ->
      if (lineIdx > 0) append('\n')
      val commentMatch = comments.find(line)
      val codePart =
        if (commentMatch != null) line.substring(0, commentMatch.range.first) else line
      val commentPart = commentMatch?.value.orEmpty()

      val styles = Array(codePart.length) { baseColor }
      keywords.findAll(codePart).forEach { m ->
        for (i in m.range) styles[i] = keywordColor
      }
      strings.findAll(codePart).forEach { m ->
        for (i in m.range) styles[i] = stringColor
      }

      var i = 0
      while (i < codePart.length) {
        val c = styles[i]
        var j = i + 1
        while (j < codePart.length && styles[j] == c) j++
        withStyle(SpanStyle(color = c)) { append(codePart.substring(i, j)) }
        i = j
      }
      if (commentPart.isNotEmpty()) {
        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
          append(commentPart)
        }
      }
    }
  }
}

private fun collectChildren(parent: Node): List<Node> {
  val list = mutableListOf<Node>()
  var child = parent.firstChild
  while (child != null) {
    list += child
    child = child.next
  }
  return list
}
