package org.yonicomponents.duplicateselection

fun main() {
    val cases: List<Pair<String, () -> Unit>> = listOf(
        "SelectionPolicyTest.exactSuggestionsRetainTheRecommendedKeeper" to { SelectionPolicyTest().exactSuggestionsRetainTheRecommendedKeeper() },
        "SelectionPolicyTest.cannotCheckAllFilesEvenWhenAddingTheRecommendedKeeperLast" to { SelectionPolicyTest().cannotCheckAllFilesEvenWhenAddingTheRecommendedKeeperLast() },
        "SelectionPolicyTest.nameGroupsAreReportOnlyForUniqueContent" to { SelectionPolicyTest().nameGroupsAreReportOnlyForUniqueContent() },
        "SelectionPolicyTest.aFileMayBeSelectedFromANameGroupOnlyWhenAlsoAnExactDuplicate" to { SelectionPolicyTest().aFileMayBeSelectedFromANameGroupOnlyWhenAlsoAnExactDuplicate() },
        "SelectionPolicyTest.crossGroupSafetyProtectsANameGroupWithoutAKeeperPath" to { SelectionPolicyTest().crossGroupSafetyProtectsANameGroupWithoutAKeeperPath() },
        "SelectionPolicyTest.aRepeatedPathIsNotTwoIndependentSurvivors" to { SelectionPolicyTest().aRepeatedPathIsNotTwoIndependentSurvivors() },
        "SelectionPolicyTest.emptyAndInvalidHashGroupsDoNotAuthorizeRemoval" to { SelectionPolicyTest().emptyAndInvalidHashGroupsDoNotAuthorizeRemoval() },
        "SelectionPolicyTest.suggestedKeeperOrderIsDcimThenMarkerThenTimeThenLengthThenPath" to { SelectionPolicyTest().suggestedKeeperOrderIsDcimThenMarkerThenTimeThenLengthThenPath() },
        "SelectionPolicyTest.selectionSnapshotsCannotBeMutatedByCallers" to { SelectionPolicyTest().selectionSnapshotsCannotBeMutatedByCallers() },
        "NameRulesTest.requiredExamples" to { NameRulesTest().requiredExamples() },
        "NameRulesTest.stripsParenthesizedAndBracketedMarkers" to { NameRulesTest().stripsParenthesizedAndBracketedMarkers() },
        "NameRulesTest.stripsRepeatedStrongMarkersAndOnlyOneWeakMarker" to { NameRulesTest().stripsRepeatedStrongMarkersAndOnlyOneWeakMarker() },
        "NameRulesTest.recognizesEveryCopyKeywordCaseInsensitively" to { NameRulesTest().recognizesEveryCopyKeywordCaseInsensitively() },
        "NameRulesTest.weakMarkerNeedsExactlyOneTrailingDigit" to { NameRulesTest().weakMarkerNeedsExactlyOneTrailingDigit() },
        "NameRulesTest.preservesMeaningfulSeparatorsWhenNoMarkerExists" to { NameRulesTest().preservesMeaningfulSeparatorsWhenNoMarkerExists() },
        "NameRulesTest.caseNormalizationDoesNotDependOnDeviceLocale" to { NameRulesTest().caseNormalizationDoesNotDependOnDeviceLocale() },
        "NameRulesTest.extensionIsNotStrippedAsACopyMarker" to { NameRulesTest().extensionIsNotStrippedAsACopyMarker() },
    )
    for ((name, run) in cases) {
        run()
        println("PASS $name")
    }
    println("${cases.size} source-derived tests passed")
    runSyntheticExample()
}
