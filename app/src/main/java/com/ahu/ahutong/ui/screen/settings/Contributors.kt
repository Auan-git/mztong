package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ahu.ahutong.R
import com.ahu.ahutong.data.SchoolConfig
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.utils.openUrlSafely
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.DeveloperViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight

@Composable
fun Contributors(
    developerViewModel: DeveloperViewModel = viewModel()
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(id = R.string.contributors),
            modifier = Modifier.padding(24.dp, 32.dp),
            style = MaterialTheme.typography.headlineLarge
        )
        mapOf(
            developerViewModel.partners to stringResource(id = R.string.mine_tv_partner),
            developerViewModel.developers to stringResource(id = R.string.mine_tv_developer),
        ).forEach { (list, name) ->
            Text(
                text = name,
                modifier = Modifier.padding(horizontal = 24.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                modifier = Modifier.clip(SmoothRoundedCornerShape(32.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                list.forEach {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(4.dp))
                            .background(100.n1 withNight 20.n1)
                            .clickable { it.onclick(context) }
                            .padding(24.dp, 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // oh shit, Compose is too hard for me...
                        when (it) {
                            is DeveloperViewModel.Developer -> {
                                if (it.img.isNotBlank()) {
                                    AsyncImage(
                                        model = it.img,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(ContinuousCapsule),
                                        contentDescription = null
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = it.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = it.desc,
                                        color = 30.n1 withNight 90.n1,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (it.contact.isNotBlank()) {
                                        Text(
                                            text = "联系方式：${it.contact}",
                                            color = 50.n1 withNight 80.n1,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            is DeveloperViewModel.Partner -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = it.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = it.desc,
                                        color = 30.n1 withNight 90.n1,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                    }
                }
            }
        }
        // 开源仓库入口：意见反馈、参与贡献统一跳到 Gitee 仓库
        Text(
            text = "开源仓库",
            modifier = Modifier.padding(horizontal = 24.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(32.dp))
                .background(100.n1 withNight 20.n1),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SettingsActionRow(
                title = "项目仓库（Gitee）",
                subtitle = "查看源码、提交 Issue 或 Pull Request",
                leadingIcon = Icons.Outlined.Code,
                showDivider = true,
                onClick = { openUrlSafely(context, SchoolConfig.PROJECT_REPOSITORY_URL) }
            )
            SettingsActionRow(
                title = "问题反馈（Issue）",
                subtitle = "报告问题、提出建议",
                leadingIcon = Icons.Outlined.Feedback,
                showDivider = false,
                onClick = { openUrlSafely(context, SchoolConfig.PROJECT_ISSUES_URL) }
            )
        }
    }
}

