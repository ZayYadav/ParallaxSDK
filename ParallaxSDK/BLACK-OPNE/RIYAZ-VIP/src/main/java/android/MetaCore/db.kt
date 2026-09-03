package android.MetaCore

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object db {
    @Throws(IOException::class)
    fun isTrue(url: String): HttpURLConnection {
        val mTrue = URL(url)
        return mTrue.openConnection() as HttpURLConnection
    }
}