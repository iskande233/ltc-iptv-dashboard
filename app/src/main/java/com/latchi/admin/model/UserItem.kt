package com.latchi.admin.model

import android.os.Parcel
import android.os.Parcelable

data class UserItem(
    val rowIdx: Int,
    val code: String,
    val name: String,
    val playlistUrl: String,
    val expiresAt: String,
    val maxDevices: Int,
    val status: String,
    val registeredDevices: String,
    val linkExpiresAt: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: "Active",
        parcel.readString() ?: "None",
        parcel.readString() ?: ""
    )

    fun getServerName(): String {
        if (playlistUrl.isBlank()) return "بدون سيرفر"
        return try {
            var s = playlistUrl.substringAfter("://").substringBefore("/")
            if (s.contains(":")) s = s.substringBefore(":")
            s.ifBlank { "سيرفر خاص" }
        } catch (_: Exception) { "سيرفر خاص" }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(rowIdx)
        parcel.writeString(code)
        parcel.writeString(name)
        parcel.writeString(playlistUrl)
        parcel.writeString(expiresAt)
        parcel.writeInt(maxDevices)
        parcel.writeString(status)
        parcel.writeString(registeredDevices)
        parcel.writeString(linkExpiresAt)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<UserItem> {
        override fun createFromParcel(parcel: Parcel): UserItem = UserItem(parcel)
        override fun newArray(size: Int): Array<UserItem?> = arrayOfNulls(size)
    }
}
