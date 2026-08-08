package com.example.data.local

data class LicBranch(
    val name: String,
    val code: String,
    val city: String = ""
)

val licBranches = listOf(
    LicBranch("Angul", "58E"),
    LicBranch("Aska", "58F"),
    LicBranch("Balasore", "596"),
    LicBranch("Balugaon", "58X"),
    LicBranch("Barbil", "58K"),
    LicBranch("Bargarh", "438"),
    LicBranch("Baripada", "598"),
    LicBranch("Berhampur CBO-I", "585"),
    LicBranch("Berhampur CAB", "57B"),
    LicBranch("Bhadrak", "599"),
    LicBranch("Bhawanipatna", "437"),
    LicBranch("Bhubaneswar CBO-I", "586"),
    LicBranch("Bhubaneswar CBO-II", "587"),
    LicBranch("Bhubaneswar CBO-III", "588"),
    LicBranch("Bhubaneswar CAB", "57A"),
    LicBranch("Bolangir", "439"),
    LicBranch("Boudh", "58H"),
    LicBranch("Cuttack CBO-I", "589"),
    LicBranch("Cuttack CBO-II", "590"),
    LicBranch("Cuttack CAB", "57C"),
    LicBranch("Dhenkanal", "591"),
    LicBranch("Jajpur Road", "58J"),
    LicBranch("Jagatsinghpur", "3943"),
    LicBranch("Jeypore", "597"),
    LicBranch("Jharsuguda", "58M"),
    LicBranch("Kendrapara", "58N"),
    LicBranch("Keonjhar", "592"),
    LicBranch("Koraput", "593"),
    LicBranch("Nayagarh", "58R"),
    LicBranch("Nimapara", "58S"),
    LicBranch("Paradeep", "58T"),
    LicBranch("Phulbani", "58U"),
    LicBranch("Puri", "594"),
    LicBranch("Rayagada", "595"),
    LicBranch("Rourkela", "436"),
    LicBranch("Sambalpur", "435"),
    LicBranch("Sonepur", "58V"),
    LicBranch("Talcher", "58W")
)

object LicBranchMaster {
    val defaultBranches: List<LicBranch> get() = licBranches

    fun findBranchByCode(code: String): LicBranch? {
        val trimmed = code.trim().uppercase()
        if (trimmed.isEmpty()) return null
        return licBranches.find { it.code.uppercase() == trimmed }
    }
}
