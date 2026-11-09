package com.guruswarupa.launch.utils

import java.math.BigDecimal
import java.math.RoundingMode

object UnitConverter {

    fun convertLength(value: BigDecimal, from: String, to: String): BigDecimal {
        val toMeters = when (from.lowercase()) {
            "mm" -> value.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            "cm" -> value.divide(BigDecimal(100), 20, RoundingMode.HALF_UP)
            "m" -> value
            "km" -> value.multiply(BigDecimal(1000))
            "in" -> value.multiply(BigDecimal("0.0254"))
            "ft" -> value.multiply(BigDecimal("0.3048"))
            "yd" -> value.multiply(BigDecimal("0.9144"))
            "mi" -> value.multiply(BigDecimal("1609.344"))
            else -> value
        }

        return when (to.lowercase()) {
            "mm" -> toMeters.multiply(BigDecimal(1000))
            "cm" -> toMeters.multiply(BigDecimal(100))
            "m" -> toMeters
            "km" -> toMeters.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            "in" -> toMeters.divide(BigDecimal("0.0254"), 20, RoundingMode.HALF_UP)
            "ft" -> toMeters.divide(BigDecimal("0.3048"), 20, RoundingMode.HALF_UP)
            "yd" -> toMeters.divide(BigDecimal("0.9144"), 20, RoundingMode.HALF_UP)
            "mi" -> toMeters.divide(BigDecimal("1609.344"), 20, RoundingMode.HALF_UP)
            else -> toMeters
        }
    }

    fun convertArea(value: BigDecimal, from: String, to: String): BigDecimal {
        val toSqMeters = when (from.lowercase()) {
            "mm²" -> value.divide(BigDecimal(1000000), 20, RoundingMode.HALF_UP)
            "cm²" -> value.divide(BigDecimal(10000), 20, RoundingMode.HALF_UP)
            "m²" -> value
            "km²" -> value.multiply(BigDecimal(1000000))
            "in²" -> value.multiply(BigDecimal("0.00064516"))
            "ft²" -> value.multiply(BigDecimal("0.092903"))
            "yd²" -> value.multiply(BigDecimal("0.836127"))
            "ac" -> value.multiply(BigDecimal("4046.86"))
            "ha" -> value.multiply(BigDecimal(10000))
            else -> value
        }

        return when (to.lowercase()) {
            "mm²" -> toSqMeters.multiply(BigDecimal(1000000))
            "cm²" -> toSqMeters.multiply(BigDecimal(10000))
            "m²" -> toSqMeters
            "km²" -> toSqMeters.divide(BigDecimal(1000000), 20, RoundingMode.HALF_UP)
            "in²" -> toSqMeters.divide(BigDecimal("0.00064516"), 20, RoundingMode.HALF_UP)
            "ft²" -> toSqMeters.divide(BigDecimal("0.092903"), 20, RoundingMode.HALF_UP)
            "yd²" -> toSqMeters.divide(BigDecimal("0.836127"), 20, RoundingMode.HALF_UP)
            "ac" -> toSqMeters.divide(BigDecimal("4046.86"), 20, RoundingMode.HALF_UP)
            "ha" -> toSqMeters.divide(BigDecimal(10000), 20, RoundingMode.HALF_UP)
            else -> toSqMeters
        }
    }

    fun convertTemperature(value: BigDecimal, from: String, to: String): BigDecimal {
        val toCelsius = when (from.uppercase()) {
            "C" -> value
            "F" -> (value - BigDecimal(32)) * BigDecimal(5) / BigDecimal(9)
            "K" -> value - BigDecimal("273.15")
            else -> value
        }

        return when (to.uppercase()) {
            "C" -> toCelsius
            "F" -> toCelsius * BigDecimal(9) / BigDecimal(5) + BigDecimal(32)
            "K" -> toCelsius + BigDecimal("273.15")
            else -> toCelsius
        }
    }

    fun convertVolume(value: BigDecimal, from: String, to: String): BigDecimal {
        val toLiters = when (from.lowercase()) {
            "ml" -> value.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            "l" -> value
            "m³" -> value.multiply(BigDecimal(1000))
            "fl oz" -> value.multiply(BigDecimal("0.0295735"))
            "cup" -> value.multiply(BigDecimal("0.236588"))
            "pt" -> value.multiply(BigDecimal("0.473176"))
            "qt" -> value.multiply(BigDecimal("0.946353"))
            "gal" -> value.multiply(BigDecimal("3.78541"))
            else -> value
        }

        return when (to.lowercase()) {
            "ml" -> toLiters.multiply(BigDecimal(1000))
            "l" -> toLiters
            "m³" -> toLiters.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            "fl oz" -> toLiters.divide(BigDecimal("0.0295735"), 20, RoundingMode.HALF_UP)
            "cup" -> toLiters.divide(BigDecimal("0.236588"), 20, RoundingMode.HALF_UP)
            "pt" -> toLiters.divide(BigDecimal("0.473176"), 20, RoundingMode.HALF_UP)
            "qt" -> toLiters.divide(BigDecimal("0.946353"), 20, RoundingMode.HALF_UP)
            "gal" -> toLiters.divide(BigDecimal("3.78541"), 20, RoundingMode.HALF_UP)
            else -> toLiters
        }
    }

    fun convertMass(value: BigDecimal, from: String, to: String): BigDecimal {
        val toKg = when (from.lowercase()) {
            "mg" -> value.divide(BigDecimal(1000000), 20, RoundingMode.HALF_UP)
            "g" -> value.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            "kg" -> value
            "oz" -> value.multiply(BigDecimal("0.0283495"))
            "lb" -> value.multiply(BigDecimal("0.453592"))
            "t" -> value.multiply(BigDecimal(1000))
            else -> value
        }

        return when (to.lowercase()) {
            "mg" -> toKg.multiply(BigDecimal(1000000))
            "g" -> toKg.multiply(BigDecimal(1000))
            "kg" -> toKg
            "oz" -> toKg.divide(BigDecimal("0.0283495"), 20, RoundingMode.HALF_UP)
            "lb" -> toKg.divide(BigDecimal("0.453592"), 20, RoundingMode.HALF_UP)
            "t" -> toKg.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            else -> toKg
        }
    }

    fun convertData(value: BigDecimal, from: String, to: String): BigDecimal {
        val toBytes = when (from.uppercase()) {
            "B" -> value
            "KB" -> value.multiply(BigDecimal(1024))
            "MB" -> value.multiply(BigDecimal(1048576))
            "GB" -> value.multiply(BigDecimal(1073741824))
            "TB" -> value.multiply(BigDecimal("1099511627776"))
            else -> value
        }

        return when (to.uppercase()) {
            "B" -> toBytes
            "KB" -> toBytes.divide(BigDecimal(1024), 20, RoundingMode.HALF_UP)
            "MB" -> toBytes.divide(BigDecimal(1048576), 20, RoundingMode.HALF_UP)
            "GB" -> toBytes.divide(BigDecimal(1073741824), 20, RoundingMode.HALF_UP)
            "TB" -> toBytes.divide(BigDecimal("1099511627776"), 20, RoundingMode.HALF_UP)
            else -> toBytes
        }
    }

    fun convertSpeed(value: BigDecimal, from: String, to: String): BigDecimal {
        val toMps = when (from.lowercase()) {
            "m/s" -> value
            "km/h" -> value.divide(BigDecimal("3.6"), 20, RoundingMode.HALF_UP)
            "mph" -> value.multiply(BigDecimal("0.44704"))
            "ft/s" -> value.multiply(BigDecimal("0.3048"))
            "knot" -> value.multiply(BigDecimal("0.514444"))
            else -> value
        }

        return when (to.lowercase()) {
            "m/s" -> toMps
            "km/h" -> toMps.multiply(BigDecimal("3.6"))
            "mph" -> toMps.divide(BigDecimal("0.44704"), 20, RoundingMode.HALF_UP)
            "ft/s" -> toMps.divide(BigDecimal("0.3048"), 20, RoundingMode.HALF_UP)
            "knot" -> toMps.divide(BigDecimal("0.514444"), 20, RoundingMode.HALF_UP)
            else -> toMps
        }
    }

    fun convertTime(value: BigDecimal, from: String, to: String): BigDecimal {
        val toSeconds = when (from.lowercase()) {
            "ms" -> value.divide(BigDecimal(1000), 20, RoundingMode.HALF_UP)
            "s" -> value
            "min" -> value.multiply(BigDecimal(60))
            "h" -> value.multiply(BigDecimal(3600))
            "d" -> value.multiply(BigDecimal(86400))
            "wk" -> value.multiply(BigDecimal(604800))
            else -> value
        }

        return when (to.lowercase()) {
            "ms" -> toSeconds.multiply(BigDecimal(1000))
            "s" -> toSeconds
            "min" -> toSeconds.divide(BigDecimal(60), 20, RoundingMode.HALF_UP)
            "h" -> toSeconds.divide(BigDecimal(3600), 20, RoundingMode.HALF_UP)
            "d" -> toSeconds.divide(BigDecimal(86400), 20, RoundingMode.HALF_UP)
            "wk" -> toSeconds.divide(BigDecimal(604800), 20, RoundingMode.HALF_UP)
            else -> toSeconds
        }
    }
}
