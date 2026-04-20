package com.cloud.quiethoursnotificationhelper

import com.cloud.core.objects.Config
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Session

const val EMAIL = "paluss1122@gmail.com"
const val APP_PASSWORD = Config.GMAILPASSWORD

fun checkNewMail(): List<Map<String, String>> {
    val props = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", "imap.gmail.com")
        put("mail.imaps.port", "993")
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.peek", "false")
    }
    val session = Session.getInstance(props)
    val store = session.getStore("imaps")
    store.connect("imap.gmail.com", EMAIL, APP_PASSWORD)

    val inbox = store.getFolder("INBOX")
    inbox.open(Folder.READ_WRITE)

    val totalMessages = inbox.messageCount
    val start = maxOf(1, totalMessages - 49)
    val messages = inbox.getMessages(start, totalMessages)
    val emails = messages.map { msg ->
        val body = msg.content.toString()
        val from = msg.from?.firstOrNull()?.toString() ?: "Unbekannt"
        val subject = msg.subject ?: "Kein Betreff"

        msg.setFlag(Flags.Flag.SEEN, true)

        mapOf(
            "from" to from,
            "subject" to subject,
            "body" to body,
            "date" to (msg.sentDate?.time?.toString() ?: "0")
        )
    }

    inbox.close(false)
    store.close()
    return emails
}