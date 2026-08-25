package com.tenmilelabs.chefai.mealplans.ui.detail.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import com.tenmilelabs.chefai.mealplans.domain.print.MealPlanPrintDocument

/**
 * Opens the system print dialog for [document], landscape by default. The dialog itself offers
 * "Save as PDF" alongside any configured printers, so this one call covers both printing the plan
 * and exporting it as a file — no [android.content.Intent]/`FileProvider` needed.
 */
fun printMealPlan(context: Context, document: MealPlanPrintDocument) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
        .build()
    printManager.print(
        "${document.planName} — ChefAI",
        MealPlanPrintAdapter(document),
        attributes,
    )
}
