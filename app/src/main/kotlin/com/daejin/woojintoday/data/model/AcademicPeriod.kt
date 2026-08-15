package com.daejin.woojintoday.data.model

import java.time.LocalDate

/** 1학기: Jan–Jun, 2학기: Jul–Dec. */
object AcademicPeriod {
    fun current(date: LocalDate = LocalDate.now()): Pair<Int, Int> =
        date.year to (if (date.monthValue <= 6) 1 else 2)

    fun next(year: Int, semester: Int): Pair<Int, Int> =
        if (semester == 1) year to 2 else (year + 1) to 1

    fun previous(year: Int, semester: Int): Pair<Int, Int> =
        if (semester == 1) (year - 1) to 2 else year to 1
}
