package com.xialia.tactkit.ui.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.xialia.tactkit.ui.components.TactKitMorphLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Reusable M3 dialog for decrypting a password-protected PDF into temporary cache memory
 * without altering the user's original document.
 */
@Composable
fun PdfPasswordUnlockDialog(
  isVerifying: Boolean,
  errorText: String?,
  onSubmitPassword: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var showPlaintext by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isVerifying) onDismiss() },
    icon = {
      Icon(
        imageVector = Icons.Rounded.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = "此 PDF 受密码保护",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "请输入打开密码以在本地临时解密预览，原文件不会被修改。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("PDF 打开密码") },
          singleLine = true,
          enabled = !isVerifying,
          isError = errorText != null,
          visualTransformation =
            if (showPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showPlaintext = !showPlaintext }) {
              Icon(
                imageVector =
                  if (showPlaintext) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = "切换密码显示",
              )
            }
          },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions =
            KeyboardActions(
              onDone = {
                if (password.isNotEmpty() && !isVerifying) {
                  onSubmitPassword(password)
                }
              }
            ),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("pdf_unlock_password_input"),
        )
        if (errorText != null) {
          Text(
            text = errorText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("pdf_unlock_error_text"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        enabled = password.isNotEmpty() && !isVerifying,
        onClick = { onSubmitPassword(password) },
        modifier = Modifier.testTag("pdf_unlock_submit_btn"),
      ) {
        if (isVerifying) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            TactKitMorphLoadingIndicator(
              size = 18.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("解密中...")
          }
        } else {
          Text("解锁预览")
        }
      }
    },
    dismissButton = {
      TextButton(enabled = !isVerifying, onClick = onDismiss) {
        Text("取消")
      }
    },
  )
}
