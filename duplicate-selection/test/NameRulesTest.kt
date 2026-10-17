package org.yonicomponents.duplicateselection

import java.util.Locale
import org.yonicomponents.duplicateselection.testing.assertEquals

class NameRulesTest {
    fun requiredExamples() {
        assertName("IMG_1234 (1).JPG", "img_1234", "jpg", Marker.STRONG)
        assertName("IMG_1234_1.jpg", "img_1234", "jpg", Marker.WEAK)
        assertName("report - Copy.pdf", "report", "pdf", Marker.STRONG)
        assertName("עותק של חוזה.PDF", "חוזה", "pdf", Marker.STRONG)
        assertName("photocopy.pdf", "photocopy", "pdf", Marker.NONE)
        assertName("Chapter 2.pdf", "chapter", "pdf", Marker.WEAK)
    }

    fun stripsParenthesizedAndBracketedMarkers() {
        assertName("report(12).pdf", "report", "pdf", Marker.STRONG)
        assertName("report (123).pdf", "report", "pdf", Marker.STRONG)
        assertName("report [1].pdf", "report", "pdf", Marker.STRONG)
        assertName("report [123].pdf", "report", "pdf", Marker.STRONG)
        assertName("report (1234).pdf", "report (1234)", "pdf", Marker.NONE)
    }

    fun stripsRepeatedStrongMarkersAndOnlyOneWeakMarker() {
        assertName("copy of עותק של report - Copy (2) [3].pdf", "report", "pdf", Marker.STRONG)
        assertName("report (1)_2.pdf", "report", "pdf", Marker.STRONG)
        assertName("report_1 (2).pdf", "report", "pdf", Marker.STRONG)
        assertName("report_1_2.pdf", "report_1", "pdf", Marker.WEAK)
        assertName("report_1 (2)_3.pdf", "report_1", "pdf", Marker.STRONG)
    }

    fun recognizesEveryCopyKeywordCaseInsensitively() {
        for (word in listOf("COPY", "עותק", "kopie", "CoPiE", "kopia")) {
            assertName("report - $word.pdf", "report", "pdf", Marker.STRONG)
            assertName("report_$word 12.pdf", "report", "pdf", Marker.STRONG)
            assertName("report-$word (2).pdf", "report", "pdf", Marker.STRONG)
        }
    }

    fun weakMarkerNeedsExactlyOneTrailingDigit() {
        for (separator in listOf(" ", "_", "-", "~")) {
            assertName("x${separator}1.txt", "x", "txt", Marker.WEAK)
            assertName("x${separator}12.txt", "x${separator}12", "txt", Marker.NONE)
        }
        assertName("x1.txt", "x1", "txt", Marker.NONE)
        assertName("Chapter 2 appendix.pdf", "chapter 2 appendix", "pdf", Marker.NONE)
    }

    fun preservesMeaningfulSeparatorsWhenNoMarkerExists() {
        assertName("report -.pdf", "report -", "pdf", Marker.NONE)
        assertName("report_.pdf", "report_", "pdf", Marker.NONE)
        assertName("report - photocopy.pdf", "report - photocopy", "pdf", Marker.NONE)
        assertName("report copycat.pdf", "report copycat", "pdf", Marker.NONE)
    }

    fun caseNormalizationDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertName("INVOICE (1).TIFF", "invoice", "tiff", Marker.STRONG)
        } finally {
            Locale.setDefault(previous)
        }
    }

    fun extensionIsNotStrippedAsACopyMarker() {
        assertName("archive.tar (1).GZ", "archive.tar", "gz", Marker.STRONG)
        assertName("report.1", "report", "1", Marker.NONE)
        assertName("report (1)", "report", "", Marker.STRONG)
        assertName("report.", "report.", "", Marker.NONE)
        assertName(".config", ".config", "", Marker.NONE)
    }

    private fun assertName(input: String, stem: String, extension: String, marker: Marker) {
        assertEquals(input, NormalizedName(stem, extension, marker), NameRules.normalize(input))
    }
}
