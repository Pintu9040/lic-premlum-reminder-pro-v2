package com.example.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class OdishaHoliday(
    val date: LocalDate,
    val name: String,
    val category: String = "Odisha State Government Holiday",
    val description: String = "Official Odisha Government Public Holiday"
)

object OdishaHolidays {

    val HOLIDAYS_2026: List<OdishaHoliday> = listOf(
        OdishaHoliday(LocalDate.of(2026, 1, 1), "New Year's Day", description = "New Year Celebration"),
        OdishaHoliday(LocalDate.of(2026, 1, 14), "Makara Sankranti / Pongal", description = "Makara Sankranti Festival"),
        OdishaHoliday(LocalDate.of(2026, 1, 23), "Netaji Subhas Chandra Bose Jayanti / Veer Surendra Sai Jayanti", description = "Birth anniversary of Netaji & Veer Surendra Sai"),
        OdishaHoliday(LocalDate.of(2026, 1, 26), "Republic Day", description = "National Republic Day"),
        OdishaHoliday(LocalDate.of(2026, 2, 15), "Maha Shivaratri", description = "Maha Shivaratri Festival"),
        OdishaHoliday(LocalDate.of(2026, 2, 23), "Vasant Panchami / Saraswati Puja", description = "Saraswati Puja"),
        OdishaHoliday(LocalDate.of(2026, 3, 3), "Dola Purnima", description = "Dola Purnima Festival"),
        OdishaHoliday(LocalDate.of(2026, 3, 4), "Holi", description = "Holi Festival of Colors"),
        OdishaHoliday(LocalDate.of(2026, 3, 20), "Id-ul-Fitr (Eid)", description = "Eid-ul-Fitr"),
        OdishaHoliday(LocalDate.of(2026, 4, 1), "Utkal Divas (Odisha Day)", description = "Odisha Foundation Day"),
        OdishaHoliday(LocalDate.of(2026, 4, 3), "Good Friday", description = "Good Friday Observance"),
        OdishaHoliday(LocalDate.of(2026, 4, 14), "Maha Vishuva Sankranti / Pana Sankranti / Dr. B.R. Ambedkar Jayanti", description = "Odia New Year & Ambedkar Jayanti"),
        OdishaHoliday(LocalDate.of(2026, 5, 1), "May Day / Labour Day", description = "International Workers' Day"),
        OdishaHoliday(LocalDate.of(2026, 5, 27), "Id-ul-Zuha (Bakrid)", description = "Bakrid Festival"),
        OdishaHoliday(LocalDate.of(2026, 5, 31), "Buddha Purnima", description = "Buddha Purnima"),
        OdishaHoliday(LocalDate.of(2026, 6, 15), "Raja Sankranti", description = "Raja Parba Odisha Festival"),
        OdishaHoliday(LocalDate.of(2026, 6, 27), "Ratha Yatra (Car Festival)", description = "Puri Lord Jagannath Ratha Yatra"),
        OdishaHoliday(LocalDate.of(2026, 7, 26), "Muharram", description = "Muharram Observance"),
        OdishaHoliday(LocalDate.of(2026, 8, 15), "Independence Day", description = "National Independence Day"),
        OdishaHoliday(LocalDate.of(2026, 8, 26), "Jhulan Purnima / Raksha Bandhan", description = "Raksha Bandhan / Gamha Purnima"),
        OdishaHoliday(LocalDate.of(2026, 9, 4), "Janmashtami", description = "Sri Krishna Janmashtami"),
        OdishaHoliday(LocalDate.of(2026, 9, 15), "Nuakhai", description = "Agricultural Harvest Festival of Odisha"),
        OdishaHoliday(LocalDate.of(2026, 9, 16), "Ganesh Puja", description = "Ganesh Chaturthi"),
        OdishaHoliday(LocalDate.of(2026, 10, 2), "Gandhi Jayanti", description = "Mahatma Gandhi Jayanti"),
        OdishaHoliday(LocalDate.of(2026, 10, 18), "Durga Puja (Maha Saptami)", description = "Durga Puja Saptami"),
        OdishaHoliday(LocalDate.of(2026, 10, 19), "Durga Puja (Maha Ashtami)", description = "Durga Puja Ashtami"),
        OdishaHoliday(LocalDate.of(2026, 10, 20), "Durga Puja (Maha Navami / Vijaya Dashami)", description = "Dussehra / Vijaya Dashami"),
        OdishaHoliday(LocalDate.of(2026, 10, 26), "Kumar Purnima", description = "Kumar Purnima Festival"),
        OdishaHoliday(LocalDate.of(2026, 11, 8), "Kali Puja / Diwali", description = "Deepavali Festival of Lights"),
        OdishaHoliday(LocalDate.of(2026, 11, 24), "Rahas Purnima / Kartika Purnima", description = "Boita Bandana / Kartika Purnima"),
        OdishaHoliday(LocalDate.of(2026, 12, 25), "Christmas Day", description = "Christmas Day")
    )

    fun getHoliday(date: LocalDate): OdishaHoliday? {
        val exact = HOLIDAYS_2026.find { it.date == date }
        if (exact != null) return exact

        return HOLIDAYS_2026.find {
            it.date.month == date.month && it.date.dayOfMonth == date.dayOfMonth
        }
    }

    fun isGovernmentHoliday(date: LocalDate): Boolean {
        return getHoliday(date) != null
    }

    fun formatHolidayDate(date: LocalDate): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH)
            date.format(formatter)
        } catch (e: Exception) {
            date.toString()
        }
    }
}
