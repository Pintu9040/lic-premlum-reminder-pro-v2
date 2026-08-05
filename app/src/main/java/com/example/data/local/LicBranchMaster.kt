package com.example.data.local

data class LicBranch(
    val code: String,
    val name: String,
    val city: String = ""
)

object LicBranchMaster {
    val defaultBranches = listOf(
        LicBranch("02A", "Balasore Branch", "Balasore"),
        LicBranch("08B", "Bhubaneswar Branch", "Bhubaneswar"),
        LicBranch("01A", "Cuttack Main Branch", "Cuttack"),
        LicBranch("03C", "Rourkela Branch", "Rourkela"),
        LicBranch("04D", "Puri Branch", "Puri"),
        LicBranch("05E", "Sambalpur Branch", "Sambalpur"),
        LicBranch("06F", "Berhampur Branch", "Berhampur"),
        LicBranch("07G", "Baripada Branch", "Baripada"),
        LicBranch("09H", "Angul Branch", "Angul"),
        LicBranch("10I", "Jharsuguda Branch", "Jharsuguda"),
        LicBranch("883", "City Center Branch", "Metropolitan"),
        LicBranch("894", "LIC Central Branch", "Central Office"),
        LicBranch("901", "Metro Divisional Branch", "Divisional"),
        LicBranch("915", "South Extension Branch", "Capital Region")
    )

    fun findBranchByCode(code: String): LicBranch? {
        val trimmed = code.trim().uppercase()
        if (trimmed.isEmpty()) return null
        return defaultBranches.find { it.code.uppercase() == trimmed }
    }
}
