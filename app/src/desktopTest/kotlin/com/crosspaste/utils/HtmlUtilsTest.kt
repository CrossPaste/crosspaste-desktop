package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

class HtmlUtilsTest {

    val htmlUtils = getHtmlUtils()

    @Test
    fun testRGBStyle() {
        val html =
            "<meta charset='utf-8'>\n" +
                "<h1 class=\"Box-sc-g0xbh4-0 dZmqGw prc-PageHeader-Title-LKOsd prc-Heading-Heading-6CmGO\"" +
                " data-component=\"PH_Title\" data-hidden=\"false\" tabindex=\"-1\" style=\"box-sizing: " +
                "border-box; font-size: 32px; margin: 0px 8px 0px 0px; font-weight: 400; display: block; " +
                "order: 1; line-height: 1; color: rgb(31, 35, 40); font-family: -apple-system, " +
                "&quot;system-ui&quot;, &quot;Segoe UI&quot;, &quot;Noto Sans&quot;" +
                ", Helvetica, Arial, sans-serif, &quot;Apple Color Emoji&quot;, &quot;Segoe UI Emoji&quot;;" +
                " font-style: normal; font- ValidIdentifiers.Datatype.variant -ligatures: normal;" +
                " font- ValidIdentifiers.Datatype.variant -caps: normal; letter-spacing: normal;" +
                " orphans: 2; text-align: start; text-indent: 0px; text-transform: none; widows: 2;" +
                " word-spacing: 0px; -webkit-text-stroke-width: 0px; white-space: normal;" +
                " background-color: rgb(255, 255, 255); text-decoration-thickness: initial;" +
                " text-decoration-style: initial; text-decoration-color: initial;" +
                " --custom-font-size: 26px,26px,2rem,2rem; --custom-line-height: 1; " +
                "--custom-font-weight: normal;\">\n" +
                "<bdi class=\"Box-sc-g0xbh4-0 lhNOUb markdown-title\" data-testid=\"issue-title\" " +
                "style=\"box-sizing: border-box; display: inline; word-break: break-word;\">" +
                "Add pasteboard character count text</bdi>\n" +
                "</h1>"

        assertTrue { htmlUtils.getBackgroundColor(html) == Color.White }
    }

    @Test
    fun testNamedColor() {
        val html =
            "<meta charset='utf-8'><pre class=\"code-block__code !my-0 !rounded-lg !text-sm !leading-relaxed\" " +
                "style=\"background: transparent; \"></pre>"
        assertTrue { htmlUtils.getBackgroundColor(html) == Color.White }
    }
}
