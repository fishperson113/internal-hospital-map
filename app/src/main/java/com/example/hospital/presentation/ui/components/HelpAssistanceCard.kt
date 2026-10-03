package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization

@Composable
fun HelpAssistanceCard(
    helpStatus: String?,
    language: AppLanguage,
    onAskHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    val statusText = when (helpStatus) {
        "sending" -> l.t(language, "Đang gửi yêu cầu hỗ trợ…", "Sending help request…")
        "open", "waiting" -> l.t(language, "Đã gửi yêu cầu. Nhân viên sẽ tới chỗ bạn.", "Help requested. Staff will come to you.")
        "coming", "assigned" -> l.t(language, "Nhân viên đang tới hỗ trợ bạn.", "A staff member is on the way.")
        "done", "resolved" -> l.t(language, "Yêu cầu hỗ trợ đã xong.", "Help request closed.")
        else -> null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (statusText != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (helpStatus) {
                    "done", "resolved" -> Color(0xFFDCFCE7)
                    else -> Color(0xFFFEF3C7)
                },
                border = BorderStroke(
                    1.dp,
                    when (helpStatus) {
                        "done", "resolved" -> Color(0xFF86EFAC)
                        else -> Color(0xFFFDE68A)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = when (helpStatus) {
                            "done", "resolved" -> Color(0xFF16A34A)
                            else -> Color(0xFFD97706)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = statusText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (helpStatus) {
                            "done", "resolved" -> Color(0xFF15803D)
                            else -> Color(0xFF92400E)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Show "Tôi bị lạc" button when not actively waiting / assigned
        val isPending = helpStatus != null && helpStatus != "done" && helpStatus != "resolved"
        if (!isPending) {
            Button(
                onClick = onAskHelp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_ask_help")
            ) {
                Icon(
                    imageVector = Icons.Default.Emergency,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = l.t(language, "Tôi bị lạc", "I am lost"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
