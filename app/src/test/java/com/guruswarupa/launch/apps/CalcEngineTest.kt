package com.guruswarupa.launch.apps

import com.guruswarupa.launch.apps.calculator.CalcEngine
import com.guruswarupa.launch.apps.calculator.CalcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalcEngineTest {
    private fun ev(s: String, deg: Boolean = true) = CalcEngine.format(CalcEngine.evaluate(s, deg))

    @Test fun precedence() {
        assertEquals("14", ev("2+3*4"))
        assertEquals("20", ev("(2+3)*4"))
        assertEquals("2.5", ev("10÷4"))
        assertEquals("7", ev("1+2×3"))
    }

    @Test fun powersAndUnary() {
        assertEquals("512", ev("2^3^2"))
        assertEquals("-4", ev("-2^2"))
        assertEquals("8", ev("2^3"))
        assertEquals("5", ev("--5"))
    }

    @Test fun percent() {
        assertEquals("0.5", ev("50%"))
        assertEquals("220", ev("200+10%"))
        assertEquals("180", ev("200-10%"))
        assertEquals("20", ev("200×10%"))
    }

    @Test fun factorialAndFunctions() {
        assertEquals("120", ev("5!"))
        assertEquals("4", ev("sqrt(16)"))
        assertEquals("4", ev("√16"))
        assertEquals("0.5", ev("sin(30)"))
        assertEquals("1", ev("cos(0)"))
        assertEquals("0", ev("sin(180)"))
        assertEquals("30", ev("asin(0.5)"))
        assertEquals("2", ev("log(100)"))
        assertEquals("1", ev("ln(e)"))
        assertEquals("1", ev("sin(π/2)", deg = false))
    }

    @Test fun implicitMultiplication() {
        assertEquals("8", ev("2(3+1)"))
        assertEquals("6.28318530718", ev("2π"))
        assertEquals("6", ev("(1+2)(3-1)"))
    }

    @Test fun floatingPointCleanup() {
        assertEquals("0.3", ev("0.1+0.2"))
        assertEquals("1", ev("0.1×10"))
    }

    @Test fun unclosedParenthesisIsClosed() {
        assertEquals("20", ev("(2+3)×(1+3"))
        assertEquals("5", ev("(2+3"))
    }

    @Test fun errors() {
        assertEquals(CalcException.Kind.DIVIDE_BY_ZERO, assertThrows(CalcException::class.java) { ev("1÷0") }.kind)
        assertEquals(CalcException.Kind.DOMAIN, assertThrows(CalcException::class.java) { ev("sqrt(-1)") }.kind)
        assertEquals(CalcException.Kind.DOMAIN, assertThrows(CalcException::class.java) { ev("tan(90)") }.kind)
        assertEquals(CalcException.Kind.SYNTAX, assertThrows(CalcException::class.java) { ev("2+") }.kind)
        assertEquals(CalcException.Kind.SYNTAX, assertThrows(CalcException::class.java) { ev("") }.kind)
        assertEquals(CalcException.Kind.RANGE, assertThrows(CalcException::class.java) { ev("171!") }.kind)
    }

    @Test fun formatting() {
        assertEquals("1000000", CalcEngine.format(1_000_000.0))
        assertEquals("0.000001", CalcEngine.format(0.000001))
        assertEquals("1E+20", CalcEngine.format(1e20))
        assertEquals("-3.5", CalcEngine.format(-3.5))
    }
}
