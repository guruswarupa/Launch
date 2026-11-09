package com.guruswarupa.launch.apps.calculator

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

class CalcException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { SYNTAX, DIVIDE_BY_ZERO, DOMAIN, RANGE }
}

object CalcEngine {

    fun evaluate(input: String, degrees: Boolean): Double {
        val text = input.replace('×', '*').replace('÷', '/').replace('−', '-').replace(" ", "")
        if (text.isEmpty()) throw CalcException(CalcException.Kind.SYNTAX)
        val result = Parser(text, degrees).parseAll()
        if (result.isNaN()) throw CalcException(CalcException.Kind.DOMAIN)
        if (result.isInfinite()) throw CalcException(CalcException.Kind.RANGE)
        return result
    }

    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val rounded = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        val abs = abs(value)
        return if (abs >= 1e15 || abs < 1e-9) {
            val s = String.format("%.9E", value)
            val (mant, exp) = s.split("E")
            val m = mant.trimEnd('0').trimEnd('.')
            "${m}E${exp.trimStart('+').let { if (it.startsWith("-")) it else "+$it" }}"
        } else {
            rounded.toPlainString()
        }
    }

    private class Parser(val s: String, val degrees: Boolean) {
        var i = 0

        fun parseAll(): Double {
            val v = expr()
            if (i < s.length) throw CalcException(CalcException.Kind.SYNTAX)
            return v
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun expr(): Double {
            var left = term()
            while (true) {
                val c = peek()
                if (c != '+' && c != '-') break
                i++
                val start = i
                var right = term()
                if (i > start && s[i - 1] == '%' && !hasOperatorBetween(start, i - 1)) right = left * right
                left = if (c == '+') left + right else left - right
            }
            return left
        }

        private fun hasOperatorBetween(from: Int, to: Int): Boolean {
            var depth = 0
            for (k in from until to) {
                when (s[k]) {
                    '(' -> depth++
                    ')' -> depth--
                    '*', '/', '+', '-' -> if (depth == 0 && k > from) return true
                }
            }
            return false
        }

        private fun term(): Double {
            var left = unary()
            while (true) {
                val c = peek()
                when {
                    c == '*' -> { i++; left *= unary() }
                    c == '/' -> {
                        i++
                        val r = unary()
                        if (r == 0.0) throw CalcException(CalcException.Kind.DIVIDE_BY_ZERO)
                        left /= r
                    }
                    startsOperand(c) -> left *= unary()
                    else -> return left
                }
            }
        }

        private fun startsOperand(c: Char) = c == '(' || c == 'π' || c == '√' || c.isLetter() || c.isDigit() || c == '.'

        private fun unary(): Double = when (peek()) {
            '-' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> power()
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') {
                i++
                val e = unary()
                return base.pow(e)
            }
            return base
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                when (peek()) {
                    '!' -> { i++; v = factorial(v) }
                    '%' -> { i++; v /= 100.0 }
                    else -> return v
                }
            }
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != Math.floor(v)) throw CalcException(CalcException.Kind.DOMAIN)
            if (v > 170) throw CalcException(CalcException.Kind.RANGE)
            var r = 1.0
            for (k in 2..v.toInt()) r *= k
            return r
        }

        private fun primary(): Double {
            val c = peek()
            if (c.isDigit() || c == '.') return number()
            if (c == '(') {
                i++
                val v = expr()
                if (peek() == ')') i++
                return v
            }
            if (c == 'π') { i++; return Math.PI }
            if (c == '√') { i++; return fn("sqrt", operand()) }
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i)
                if (name == "e" && peek() != '(') return Math.E
                if (name == "pi") return Math.PI
                if (name == "ans") throw CalcException(CalcException.Kind.SYNTAX)
                return fn(name, operand())
            }
            throw CalcException(CalcException.Kind.SYNTAX)
        }

        private fun operand(): Double {
            if (peek() == '(') {
                i++
                val v = expr()
                if (peek() == ')') i++
                return v
            }
            return postfix()
        }

        private fun number(): Double {
            val start = i
            var dot = false
            while (i < s.length && (s[i].isDigit() || (s[i] == '.' && !dot))) {
                if (s[i] == '.') dot = true
                i++
            }
            return s.substring(start, i).toDoubleOrNull() ?: throw CalcException(CalcException.Kind.SYNTAX)
        }

        private fun toRad(x: Double) = if (degrees) Math.toRadians(x) else x
        private fun fromRad(x: Double) = if (degrees) Math.toDegrees(x) else x
        private fun clean(x: Double) = if (abs(x) < 1e-12) 0.0 else x

        private fun fn(name: String, x: Double): Double = when (name) {
            "sin" -> clean(sin(toRad(x)))
            "cos" -> clean(cos(toRad(x)))
            "tan" -> {
                val r = toRad(x)
                if (abs(cos(r)) < 1e-12) throw CalcException(CalcException.Kind.DOMAIN)
                clean(tan(r))
            }
            "asin" -> { if (x < -1 || x > 1) throw CalcException(CalcException.Kind.DOMAIN); fromRad(asin(x)) }
            "acos" -> { if (x < -1 || x > 1) throw CalcException(CalcException.Kind.DOMAIN); fromRad(acos(x)) }
            "atan" -> fromRad(atan(x))
            "sinh" -> sinh(x)
            "cosh" -> cosh(x)
            "tanh" -> tanh(x)
            "ln" -> { if (x <= 0) throw CalcException(CalcException.Kind.DOMAIN); ln(x) }
            "log" -> { if (x <= 0) throw CalcException(CalcException.Kind.DOMAIN); log10(x) }
            "log2" -> { if (x <= 0) throw CalcException(CalcException.Kind.DOMAIN); log2(x) }
            "sqrt" -> { if (x < 0) throw CalcException(CalcException.Kind.DOMAIN); sqrt(x) }
            "cbrt" -> cbrt(x)
            "abs" -> abs(x)
            "exp" -> exp(x)
            else -> throw CalcException(CalcException.Kind.SYNTAX)
        }
    }
}
