package com.tenmilelabs.chefai.util

object MathUtils {

    fun removeTrailingZeros(num: String): String {
        if(!num.contains('.')) // Return the original number if it doesn't contain decimal
            return num
        return num
            .dropLastWhile { it == '0' } // Remove trailing zero
            .dropLastWhile { it == '.' } // Remove decimal in case it's the last character in the resultant string
    }
}